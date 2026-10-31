package dev.kavi.customnarrator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.kavi.customnarrator.config.NarratorConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Speaks text with a chosen voice, because Minecraft's built-in narrator only has one voice.
 *
 *   PIPER   : Piper neural TTS (offline). With the LibriTTS-R model that's 904 speakers,
 *             named "speaker 0" .. "speaker 903". Kept running in the background so the
 *             model only loads once.
 *   WINDOWS : PowerShell + System.Speech (SAPI voices, usually David and Zira)
 *   MAC     : the `say` command
 *   ESPEAK  : espeak-ng / espeak on Linux
 *   SPEECHD : speech-dispatcher (spd-say) on Linux, used if espeak-ng isn't installed
 *
 * On Linux, Piper's audio is played with pw-play (PipeWire, Fedora's default), then paplay,
 * then aplay, then Java's own audio as a last resort.
 *
 * Messages are spoken one after another on a background thread, never on the game thread.
 */
public final class VoiceEngine {

    public enum Backend { DETECTING, PIPER, WINDOWS, MAC, ESPEAK, SPEECHD, NONE }

    public static final String PIPER_PREFIX = "speaker ";

    private static volatile Backend backend = Backend.DETECTING;
    private static volatile List<String> voices = List.of();
    private static volatile String statusInfo = "";
    private static String espeakCmd = "espeak-ng";
    private static volatile List<String> linuxPlayer; // e.g. ["pw-play"], null = use Java audio

    // Windows PowerShell helper
    private static Process winProc;
    private static BufferedWriter winIn;

    // Piper
    private static Path piperExe, piperModel, piperOut;
    private static Process piperProc;
    private static BufferedWriter piperIn;
    private static BufferedReader piperOutReader;
    private static double piperLengthScale = 1.0;
    private static volatile SourceDataLine playing;

    private static volatile Process currentProc; // mac/linux clip playing right now
    private static final Gson GSON = new Gson();

    private static ExecutorService exec = newExec();

    private static ExecutorService newExec() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CustomNarrator-Voice");
            t.setDaemon(true);
            return t;
        });
    }

    private VoiceEngine() {}

    public static Backend backend() { return backend; }
    public static boolean available() { return backend != Backend.NONE && backend != Backend.DETECTING; }
    public static List<String> voices() { return voices; }
    /** Human-readable description of what's in use, for /narratorvoice status. */
    public static String statusInfo() { return statusInfo; }

    /** Detects the backend in the background so game startup isn't slowed down. */
    public static void init() {
        exec.submit(VoiceEngine::detect);
    }

    /** Stops everything and detects again (after installing Piper or changing settings). */
    public static void reload() {
        stopProcesses();
        exec.shutdownNow();
        exec = newExec();
        backend = Backend.DETECTING;
        voices = List.of();
        init();
    }

    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); }
    private static boolean isMac() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac"); }
    private static boolean isLinux() { return !isWindows() && !isMac(); }
    /** True when the game runs inside a Flatpak sandbox (for example Prism Launcher from Flathub). */
    public static boolean inFlatpak() { return System.getenv("FLATPAK_ID") != null || Files.exists(Paths.get("/.flatpak-info")); }

    private static void detect() {
        NarratorConfig cfg = NarratorConfig.get();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (isLinux()) detectLinuxPlayer();
        try {
            if (cfg.voiceBackend != NarratorConfig.VoiceBackend.SYSTEM && detectPiper(cfg)) {
                // done
            } else if (cfg.voiceBackend == NarratorConfig.VoiceBackend.PIPER) {
                backend = Backend.NONE; // user asked for Piper only, and it wasn't found
            } else if (os.contains("win")) startWindows();
            else if (os.contains("mac")) detectMac();
            else {
                detectEspeak();
                if (backend == Backend.NONE) detectSpeechd();
            }
        } catch (Exception e) {
            CustomNarratorClient.LOGGER.warn("[CustomNarrator] voice engine unavailable: {}", e.toString());
            backend = Backend.NONE;
        }
        if (backend != Backend.PIPER) {
            statusInfo = backend + ", " + voices.size() + " voices";
        }
        if (isLinux()) {
            statusInfo += " | audio: " + (linuxPlayer == null ? "Java" : linuxPlayer.get(0))
                    + (inFlatpak() ? " | Flatpak" : "");
        }
        CustomNarratorClient.LOGGER.info("[CustomNarrator] voice backend: {}", statusInfo);
    }

    /* ---------------- Piper ---------------- */

    private static boolean detectPiper(NarratorConfig cfg) throws Exception {
        boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String exeName = win ? "piper.exe" : "piper";
        String home = System.getProperty("user.home", "");

        List<Path> candidates = new ArrayList<>();
        if (!cfg.piperPath.isBlank()) candidates.add(Paths.get(cfg.piperPath.trim()));
        if (win) {
            candidates.add(Paths.get("C:\\piper"));
        }
        candidates.add(Paths.get(home, "piper"));
        if (!win) {
            candidates.add(Paths.get(home, ".local", "share", "piper"));
            candidates.add(Paths.get(home, ".local", "opt", "piper"));
            candidates.add(Paths.get("/opt/piper"));
            candidates.add(Paths.get(home, "Downloads", "piper_linux_x86_64"));
            candidates.add(Paths.get(home, "Downloads", "piper_linux_aarch64"));
        }
        try { // the game folder is always reachable, even inside a Flatpak sandbox
            candidates.add(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("piper"));
        } catch (Throwable ignored) { }
        candidates.add(Paths.get(home, "Documents", "piper"));
        candidates.add(Paths.get(home, "Desktop", "piper"));
        candidates.add(Paths.get(home, "Downloads", "piper"));
        candidates.add(Paths.get(home, "Downloads", "piper_windows_amd64"));
        candidates.add(Paths.get(home, "Downloads", "piper_windows_amd64", "piper"));

        Path exe = null;
        for (Path c : candidates) {
            exe = findExe(c, exeName);
            if (exe != null && !win && !isPiperTts(exe)) {
                CustomNarratorClient.LOGGER.warn("[CustomNarrator] {} is not Piper text-to-speech (Fedora's 'piper' package is a mouse settings app), skipping", exe);
                exe = null;
            }
            if (exe != null) break;
        }
        if (exe == null) {
            CustomNarratorClient.LOGGER.info("[CustomNarrator] Piper not found (looked in {})", candidates);
            return false;
        }

        Path model = findModel(exe.getParent(), cfg.piperModel);
        if (model == null) {
            CustomNarratorClient.LOGGER.warn("[CustomNarrator] Found Piper at {} but no voice model '{}'.onnx next to it or in a voices folder",
                    exe, cfg.piperModel);
            return false;
        }

        // Read the speaker count from the .onnx.json file.
        int speakers = 1;
        try {
            JsonObject json = GSON.fromJson(Files.readString(Paths.get(model + ".json")), JsonObject.class);
            if (json.has("num_speakers")) speakers = Math.max(1, json.get("num_speakers").getAsInt());
        } catch (Exception e) {
            CustomNarratorClient.LOGGER.warn("[CustomNarrator] could not read {}.json: {}", model, e.toString());
        }

        piperExe = exe;
        piperModel = model;
        piperOut = Files.createTempDirectory("customnarrator-piper");
        piperOut.toFile().deleteOnExit();

        List<String> list = new ArrayList<>(speakers);
        for (int i = 0; i < speakers; i++) list.add(PIPER_PREFIX + i);
        voices = List.copyOf(list);

        startPiper(rateToLengthScale(cfg.speechRate));
        backend = Backend.PIPER;
        statusInfo = "Piper, model " + model.getFileName() + ", " + speakers + " speakers (" + exe + ")";
        return true;
    }

    private static Path findExe(Path p, String exeName) {
        if (p == null) return null;
        if (Files.isRegularFile(p)) return p;                      // user gave the exe itself
        Path direct = p.resolve(exeName);
        if (Files.isRegularFile(direct)) return direct;
        Path nested = p.resolve("piper").resolve(exeName);        // zip extracts to a "piper" folder inside
        if (Files.isRegularFile(nested)) return nested;
        return null;
    }

    /** Piper TTS is a native program. Fedora's mouse app called piper is a Python script. */
    private static boolean isPiperTts(Path exe) {
        try (java.io.InputStream in = Files.newInputStream(exe)) {
            byte[] head = in.readNBytes(4);
            boolean elf = head.length == 4 && head[0] == 0x7f && head[1] == 'E' && head[2] == 'L' && head[3] == 'F';
            if (!elf) return false;
        } catch (Exception e) {
            return false;
        }
        if (!Files.isExecutable(exe)) {
            // Some file managers drop the executable bit when extracting.
            if (!exe.toFile().setExecutable(true)) {
                CustomNarratorClient.LOGGER.warn("[CustomNarrator] {} is not executable. Run: chmod +x {}", exe, exe);
                return false;
            }
        }
        return true;
    }

    private static Path findModel(Path exeDir, String name) {
        String file = name.endsWith(".onnx") ? name : name + ".onnx";
        List<Path> dirs = new ArrayList<>();
        dirs.add(exeDir);
        dirs.add(exeDir.resolve("voices"));
        if (exeDir.getParent() != null) {
            dirs.add(exeDir.getParent());
            dirs.add(exeDir.getParent().resolve("voices"));
        }
        for (Path d : dirs) {
            Path m = d.resolve(file);
            if (Files.isRegularFile(m) && Files.isRegularFile(Paths.get(m + ".json"))) return m;
        }
        return null;
    }

    /** -10..10 speech rate -> Piper length scale (smaller = faster). */
    private static double rateToLengthScale(int rate) {
        return Math.max(0.5, Math.min(1.5, 1.0 - rate * 0.05));
    }

    private static void startPiper(double lengthScale) throws Exception {
        if (piperProc != null) piperProc.destroy();
        ProcessBuilder pb = new ProcessBuilder(
                piperExe.toString(), "-m", piperModel.toString(),
                "--json-input", "--length_scale", String.format(Locale.ROOT, "%.2f", lengthScale));
        pb.directory(piperExe.getParent().toFile()); // Piper looks for espeak-ng-data next to itself
        if (!isWindows()) {
            String dir = piperExe.getParent().toString();
            String old = System.getenv("LD_LIBRARY_PATH");
            pb.environment().put("LD_LIBRARY_PATH", old == null || old.isBlank() ? dir : dir + ":" + old);
        }
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        piperProc = pb.start();
        piperIn = new BufferedWriter(new OutputStreamWriter(piperProc.getOutputStream(), StandardCharsets.UTF_8));
        piperOutReader = new BufferedReader(new InputStreamReader(piperProc.getInputStream(), StandardCharsets.UTF_8));
        piperLengthScale = lengthScale;
    }

    private static void speakPiper(String voice, int rate, String text) throws Exception {
        double scale = rateToLengthScale(rate);
        if (piperProc == null || !piperProc.isAlive() || scale != piperLengthScale) startPiper(scale);

        File wav = piperOut.resolve(System.nanoTime() + ".wav").toFile();
        JsonObject req = new JsonObject();
        req.addProperty("text", text);
        req.addProperty("speaker_id", speakerId(voice));
        req.addProperty("output_file", wav.getAbsolutePath());
        piperIn.write(asciiOnly(GSON.toJson(req)));
        piperIn.newLine();
        piperIn.flush();

        String done = piperOutReader.readLine(); // Piper prints the file path when it's finished
        if (done == null) {
            piperProc = null; // crashed, restart next time
            throw new IllegalStateException("Piper stopped unexpectedly");
        }
        try {
            playWav(wav);
        } finally {
            wav.delete();
        }
    }

    /** "speaker 123" or "123" -> 123. Anything else -> 0. */
    private static int speakerId(String voice) {
        if (voice == null) return 0;
        String s = voice.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith(PIPER_PREFIX)) s = s.substring(PIPER_PREFIX.length()).trim();
        try {
            int id = Integer.parseInt(s);
            return (id >= 0 && id < voices.size()) ? id : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Escape non-ASCII as \\uXXXX so the text survives any console code page. */
    private static String asciiOnly(String json) {
        StringBuilder b = new StringBuilder(json.length());
        for (char c : json.toCharArray()) {
            if (c < 128) b.append(c);
            else b.append(String.format("\\u%04x", (int) c));
        }
        return b.toString();
    }

    /* ---------------- audio playback ---------------- */

    private static final String[][] LINUX_PLAYERS = {
            {"pw-play"},          // PipeWire (Fedora default)
            {"paplay"},           // PulseAudio, also works on PipeWire
            {"aplay", "-q"}       // ALSA
    };

    private static void detectLinuxPlayer() {
        linuxPlayer = null;
        for (String[] cmd : LINUX_PLAYERS) {
            try {
                Process p = new ProcessBuilder(cmd[0], "--version")
                        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                if (p.waitFor() == 0) {
                    linuxPlayer = List.of(cmd);
                    return;
                }
            } catch (Exception ignored) { }
        }
    }

    private static void playWav(File wav) throws Exception {
        List<String> player = linuxPlayer;
        if (player != null) {
            List<String> cmd = new ArrayList<>(player);
            cmd.add(wav.getAbsolutePath());
            if (run(cmd) == 0) return;
            CustomNarratorClient.LOGGER.warn("[CustomNarrator] {} failed, switching to Java audio", player.get(0));
            linuxPlayer = null;
        }
        playWavJava(wav);
    }

    private static void playWavJava(File wav) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(wav)) {
            AudioFormat fmt = in.getFormat();
            byte[] pcm = in.readAllBytes();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            if (!AudioSystem.isLineSupported(info)) {
                // Many Linux sound setups only accept 44.1/48 kHz stereo. Piper makes 22.05 kHz mono.
                pcm = toStereo16(pcm, fmt, 48000f);
                fmt = new AudioFormat(48000f, 16, 2, true, false);
                info = new DataLine.Info(SourceDataLine.class, fmt);
            }
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(fmt);
            playing = line;
            line.start();
            for (int off = 0; off < pcm.length; off += 8192) {
                line.write(pcm, off, Math.min(8192, pcm.length - off));
            }
            line.drain();
            line.close();
        } finally {
            playing = null;
        }
    }

    /** Converts 16-bit little-endian PCM (mono or stereo, any rate) to 16-bit stereo at the target rate. */
    static byte[] toStereo16(byte[] pcm, AudioFormat fmt, float targetRate) {
        if (fmt.getSampleSizeInBits() != 16 || fmt.isBigEndian()) {
            throw new IllegalArgumentException("unsupported WAV format: " + fmt);
        }
        int ch = Math.max(1, fmt.getChannels());
        int frames = pcm.length / (2 * ch);
        if (frames == 0) return new byte[0];
        double step = fmt.getSampleRate() / targetRate;
        int outFrames = (int) Math.floor((frames - 1) / step) + 1;
        byte[] out = new byte[outFrames * 4];
        for (int i = 0; i < outFrames; i++) {
            double pos = i * step;
            int a = (int) pos;
            int b = Math.min(a + 1, frames - 1);
            double t = pos - a;
            for (int c = 0; c < 2; c++) {
                int src = Math.min(c, ch - 1);
                int sa = sample(pcm, (a * ch + src) * 2);
                int sb = sample(pcm, (b * ch + src) * 2);
                int v = (int) Math.round(sa + (sb - sa) * t);
                int o = (i * 2 + c) * 2;
                out[o] = (byte) v;
                out[o + 1] = (byte) (v >> 8);
            }
        }
        return out;
    }

    private static int sample(byte[] b, int i) {
        return (short) ((b[i] & 0xff) | (b[i + 1] << 8));
    }

    /* ---------------- Windows ---------------- */

    private static final String PS_SCRIPT = String.join("\n",
            "$ErrorActionPreference = 'SilentlyContinue'",
            "Add-Type -AssemblyName System.Speech",
            "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
            "$s.SetOutputToDefaultAudioDevice()",
            "$names = ($s.GetInstalledVoices() | Where-Object { $_.Enabled } | ForEach-Object { $_.VoiceInfo.Name }) -join '|'",
            "[Console]::Out.WriteLine('VOICES:' + $names)",
            "[Console]::Out.Flush()",
            "$dec = { param($b) [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b)) }",
            "while (($line = [Console]::In.ReadLine()) -ne $null) {",
            "  $p = $line.Split(\"`t\")",
            "  if ($p.Length -lt 3) { continue }",
            "  $v = & $dec $p[0]; $t = & $dec $p[2]",
            "  if ($v) { try { $s.SelectVoice($v) } catch {} }",
            "  try { $s.Rate = [int]$p[1] } catch {}",
            "  $s.Speak($t)",
            "}");

    private static void startWindows() throws Exception {
        Path script = Files.createTempFile("customnarrator", ".ps1");
        Files.writeString(script, PS_SCRIPT);
        script.toFile().deleteOnExit();
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", script.toString());
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        winProc = pb.start();
        winIn = new BufferedWriter(new OutputStreamWriter(winProc.getOutputStream(), StandardCharsets.US_ASCII));
        BufferedReader out = new BufferedReader(new InputStreamReader(winProc.getInputStream(), StandardCharsets.UTF_8));
        String first = out.readLine();
        if (first == null || !first.startsWith("VOICES:")) throw new IllegalStateException("PowerShell speech did not start");
        List<String> list = new ArrayList<>();
        for (String n : first.substring(7).split("\\|")) if (!n.isBlank()) list.add(n.trim());
        Collections.sort(list);
        voices = List.copyOf(list);
        backend = Backend.WINDOWS;
    }

    /* ---------------- macOS ---------------- */

    private static final Pattern MAC_VOICE = Pattern.compile("^(.+?)\\s+[a-z]{2,3}[_-][A-Za-z0-9]+\\s+#.*$");

    private static void detectMac() throws Exception {
        Process p = new ProcessBuilder("say", "-v", "?").redirectError(ProcessBuilder.Redirect.DISCARD).start();
        List<String> list = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                Matcher m = MAC_VOICE.matcher(line);
                if (m.matches()) list.add(m.group(1).trim());
            }
        }
        p.waitFor();
        Collections.sort(list);
        voices = List.copyOf(list);
        backend = Backend.MAC;
    }

    /* ---------------- Linux (espeak) ---------------- */

    private static final String[] ESPEAK_VARIANTS = {
            "m1", "m2", "m3", "m4", "m5", "m6", "m7",
            "f1", "f2", "f3", "f4", "f5",
            "croak", "whisper", "klatt"
    };

    private static void detectEspeak() {
        for (String cmd : new String[]{"espeak-ng", "espeak"}) {
            try {
                Process p = new ProcessBuilder(cmd, "--version")
                        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                if (p.waitFor() == 0) {
                    espeakCmd = cmd;
                    List<String> list = new ArrayList<>();
                    for (String v : ESPEAK_VARIANTS) list.add("en-us+" + v);
                    voices = List.copyOf(list);
                    backend = Backend.ESPEAK;
                    return;
                }
            } catch (Exception ignored) { }
        }
        backend = Backend.NONE;
    }

    /* ---------------- Linux (speech-dispatcher) ---------------- */

    private static final String[] SPEECHD_VOICES = {
            "male1", "male2", "male3", "female1", "female2", "female3", "child_male", "child_female"
    };

    private static void detectSpeechd() {
        try {
            Process p = new ProcessBuilder("spd-say", "--version")
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (p.waitFor() == 0) {
                voices = List.of(SPEECHD_VOICES);
                backend = Backend.SPEECHD;
            }
        } catch (Exception ignored) { }
    }

    /* ---------------- speaking ---------------- */

    /**
     * @param voice voice name ("" = default)
     * @param rate  -10 (slow) .. 10 (fast), 0 = normal
     */
    public static void speak(String voice, int rate, String text) {
        if (!available() || text == null || text.isBlank()) return;
        final String v = voice == null ? "" : voice;
        final int r = Math.max(-10, Math.min(10, rate));
        exec.submit(() -> {
            try {
                switch (backend) {
                    case PIPER -> speakPiper(v, r, text);
                    case WINDOWS -> {
                        Base64.Encoder b64 = Base64.getEncoder();
                        winIn.write(b64.encodeToString(v.getBytes(StandardCharsets.UTF_8)) + "\t" + r + "\t"
                                + b64.encodeToString(text.getBytes(StandardCharsets.UTF_8)));
                        winIn.newLine();
                        winIn.flush();
                    }
                    case MAC -> run(v.isEmpty()
                            ? List.of("say", "-r", String.valueOf(wpm(r)), "--", text)
                            : List.of("say", "-v", v, "-r", String.valueOf(wpm(r)), "--", text));
                    case ESPEAK -> run(v.isEmpty()
                            ? List.of(espeakCmd, "-s", String.valueOf(wpm(r)), "--", text)
                            : List.of(espeakCmd, "-v", v, "-s", String.valueOf(wpm(r)), "--", text));
                    case SPEECHD -> run(v.isEmpty()
                            ? List.of("spd-say", "-w", "-r", String.valueOf(r * 10), "--", text)
                            : List.of("spd-say", "-w", "-t", v, "-r", String.valueOf(r * 10), "--", text));
                    default -> { }
                }
            } catch (Exception e) {
                CustomNarratorClient.LOGGER.warn("[CustomNarrator] could not speak: {}", e.toString());
            }
        });
    }

    private static int wpm(int rate) { return 175 + rate * 15; }

    private static int run(List<String> cmd) throws Exception {
        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        currentProc = p;
        try {
            return p.waitFor();
        } finally {
            currentProc = null;
        }
    }

    /** Picks a stable voice for a player (same name -> same voice every time). */
    public static String autoVoiceFor(String playerName) {
        List<String> list = voices;
        if (list.isEmpty() || playerName == null) return "";
        // Mix the hash so similar names (Steve1, Steve2) land far apart among 904 speakers.
        int h = playerName.toLowerCase(Locale.ROOT).hashCode();
        h ^= (h >>> 16); h *= 0x45d9f3b; h ^= (h >>> 16);
        return list.get(Math.floorMod(h, list.size()));
    }

    /** Finds a voice by name, ignoring case. With Piper, "123" also means "speaker 123". Null if unknown. */
    public static String matchVoice(String name) {
        String n = name.trim();
        if (backend == Backend.PIPER && n.matches("\\d+")) n = PIPER_PREFIX + n;
        for (String v : voices) if (v.equalsIgnoreCase(n)) return v;
        return null;
    }

    private static void stopProcesses() {
        SourceDataLine l = playing;
        if (l != null) l.close();
        Process p = currentProc;
        if (p != null) p.destroy();
        if (winProc != null) { winProc.destroy(); winProc = null; }
        if (piperProc != null) { piperProc.destroy(); piperProc = null; }
    }

    public static void shutdown() {
        exec.shutdownNow();
        stopProcesses();
    }
}
