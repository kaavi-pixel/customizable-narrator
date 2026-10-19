package dev.kavi.customnarrator.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.kavi.customnarrator.CustomNarratorClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Saved to config/customnarrator.json */
public final class NarratorConfig {
    public enum Format { PLAYER_AND_MESSAGE, CUSTOM_NAME_AND_MESSAGE, MESSAGE_ONLY }

    public Format format = Format.PLAYER_AND_MESSAGE;
    public String separator = "says";
    public boolean fallbackToRealName = true;
    /** Real player name -> name the Narrator should say. */
    public Map<String, String> aliases = new LinkedHashMap<>();

    // ---- voices ----
    /** Use a separate voice per player (OS speech tools instead of the built-in narrator). */
    public boolean useCustomVoices = true;
    /** Players without a chosen voice automatically get one of the installed voices. */
    public boolean autoAssignVoices = true;
    /** Voice for players with no voice set when auto-assign is off ("" = system default). */
    public String defaultVoice = "";
    /** -10 (slow) .. 10 (fast) */
    public int speechRate = 0;
    /** Real player name -> voice name. */
    public Map<String, String> playerVoices = new LinkedHashMap<>();

    // ---- Piper (offline neural voices) ----
    public enum VoiceBackend { AUTO, PIPER, SYSTEM }
    /** AUTO = use Piper if it's installed, otherwise the computer's own voices. */
    public VoiceBackend voiceBackend = VoiceBackend.AUTO;
    /** Path to piper.exe (or its folder). Empty = look in the usual places, like C:/piper. */
    public String piperPath = "";
    /** Piper voice model name (file name without .onnx). */
    public String piperModel = "en_US-libritts_r-medium";

    private static NarratorConfig INSTANCE = new NarratorConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static NarratorConfig get() { return INSTANCE; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("customnarrator.json");
    }

    public static void load() {
        Path p = path();
        if (Files.exists(p)) {
            try {
                NarratorConfig c = GSON.fromJson(Files.readString(p), NarratorConfig.class);
                if (c != null) {
                    if (c.format == null) c.format = Format.PLAYER_AND_MESSAGE;
                    if (c.separator == null) c.separator = "";
                    if (c.aliases == null) c.aliases = new LinkedHashMap<>();
                    if (c.playerVoices == null) c.playerVoices = new LinkedHashMap<>();
                    if (c.defaultVoice == null) c.defaultVoice = "";
                    if (c.voiceBackend == null) c.voiceBackend = VoiceBackend.AUTO;
                    if (c.piperPath == null) c.piperPath = "";
                    if (c.piperModel == null || c.piperModel.isBlank()) c.piperModel = "en_US-libritts_r-medium";
                    c.speechRate = Math.max(-10, Math.min(10, c.speechRate));
                    INSTANCE = c;
                }
            } catch (Exception e) {
                CustomNarratorClient.LOGGER.error("[CustomNarrator] bad config, using defaults", e);
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(INSTANCE));
        } catch (Exception e) {
            CustomNarratorClient.LOGGER.error("[CustomNarrator] could not save config", e);
        }
    }

    /** Case-insensitive lookup; returns null if the player has no custom name. */
    public String aliasFor(String realName) { return lookup(aliases, realName); }

    /** The voice this player should be read with ("" = system default). */
    public String voiceFor(String realName) {
        String chosen = lookup(playerVoices, realName);
        // Ignore a saved voice that the current engine doesn't have (e.g. "Microsoft Zira" while using Piper).
        if (chosen != null && (dev.kavi.customnarrator.VoiceEngine.matchVoice(chosen) != null
                || dev.kavi.customnarrator.VoiceEngine.backend() == dev.kavi.customnarrator.VoiceEngine.Backend.ESPEAK)) {
            return chosen;
        }
        if (autoAssignVoices && realName != null && !realName.isBlank()) {
            String auto = dev.kavi.customnarrator.VoiceEngine.autoVoiceFor(realName);
            if (!auto.isEmpty()) return auto;
        }
        return defaultVoice;
    }

    private static String lookup(Map<String, String> map, String key) {
        if (key == null) return null;
        String exact = map.get(key);
        if (exact != null) return exact;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }

    /** Removes a key regardless of upper/lower case. */
    public static void removeIgnoreCase(Map<String, String> map, String key) {
        map.keySet().removeIf(k -> k.equalsIgnoreCase(key));
    }
}
