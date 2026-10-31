# How to Install Customizable Narrator on Fedora

For **Minecraft Java Edition 26.3** on Fedora Workstation or Fedora KDE (43 or newer). The Windows guide is [HOW_TO_INSTALL.md](HOW_TO_INSTALL.md).

## What's different on Fedora

- Minecraft's own narrator needs a library called flite, which Fedora doesn't set up for it. Normally that means Ctrl+B does nothing and you see "Narrator not available". From version 1.4.0, this mod turns the narrator setting on itself when its voices are ready, so you don't need flite.
- Voices come from Piper (904 voices) or, as a simpler option, espeak-ng.
- Audio plays through PipeWire (`pw-play`), Fedora's sound system.
- Don't run `sudo dnf install piper`. On Fedora, that's a gaming mouse app with the same name. You need the Piper voice program from step 3.

## 1. Install a launcher and Java 25

[Prism Launcher](https://prismlauncher.org/download/linux/) is the easiest way to run Fabric on Fedora. Install the regular (non-Flatpak) version from Copr:

```bash
sudo dnf copr enable g3tchoo/prismlauncher
sudo dnf install prismlauncher java-25-openjdk
```

Minecraft 26.3 needs Java 25. Fedora has it as [java-25-openjdk](https://packages.fedoraproject.org/pkgs/java-25-openjdk/java-25-openjdk/).

If you'd rather use the Flatpak version of Prism from Flathub, it works too, but Piper goes in a different folder. See [Flatpak notes](#flatpak-notes).

## 2. Make a Fabric 26.3 instance and add the mod

1. Open Prism Launcher and sign in with your Microsoft account.
2. Click **Add Instance**, pick **26.3**, set **Mod loader** to **Fabric**, and click **OK**.
3. Select the instance, click **Edit**, then go to **Mods**:
   - **Download mods** → search **Fabric API** → select it → **OK**
   - **Add file** → choose [customnarrator-1.4.0.jar](https://github.com/kaavi-pixel/customizable-narrator/raw/main/download/customnarrator-1.4.0.jar)
   - Optional: download **Cloth Config** and **Mod Menu** the same way for an in-game settings screen
4. Under **Settings** → **Java**, make sure it uses Java 25 (`/usr/lib/jvm/java-25-openjdk`).

## 3. Install Piper and the LibriTTS voice (recommended)

Open a terminal and paste this whole block:

```bash
cd ~
ARCH=$(uname -m)   # x86_64 on most PCs, aarch64 on ARM
curl -L -o piper.tar.gz "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_${ARCH}.tar.gz"
mkdir -p ~/piper/voices
tar -xzf piper.tar.gz -C ~/piper
rm piper.tar.gz

cd ~/piper/voices
curl -L -o en_US-libritts_r-medium.onnx https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/libritts_r/medium/en_US-libritts_r-medium.onnx
curl -L -o en_US-libritts_r-medium.onnx.json https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/libritts_r/medium/en_US-libritts_r-medium.onnx.json
ls -l ~/piper/piper/piper ~/piper/voices
```

You should end up with:

```
~/piper/piper/piper                               the program
~/piper/voices/en_US-libritts_r-medium.onnx       78,580,914 bytes
~/piper/voices/en_US-libritts_r-medium.onnx.json  20,123 bytes
```

If the `.onnx` file is much smaller, the download stopped early. Run that `curl` line again.

### Test Piper (optional)

```bash
echo "Hello, I am speaker forty two" | ~/piper/piper/piper -m ~/piper/voices/en_US-libritts_r-medium.onnx --speaker 42 -f /tmp/test.wav
pw-play /tmp/test.wav
```

### Simpler option: espeak-ng

If you skip Piper, install espeak-ng for 15 robotic-sounding voices:

```bash
sudo dnf install espeak-ng
```

If neither is installed, the mod tries speech-dispatcher (`spd-say`), which gives 8 voices.

## 4. Turn it on in Minecraft

1. Launch the instance and join a world or server.
2. Type `/narratorvoice status`. It should say something like:
   `Piper, model en_US-libritts_r-medium.onnx, 904 speakers (...) | audio: pw-play`
3. Press `Ctrl + B` until the toast says **Narrator Enabled: Chat** (or **All**).
4. Type `/narratorvoice test 42` to hear a voice.

The mod looks for voices a few seconds after the game starts. If you press Ctrl+B before that and see "Narrator not available", wait a moment and press it again.

## Commands

| Command | What it does |
|---|---|
| `/narratorvoice status` | Shows the voice engine and audio player in use |
| `/narratorvoice list` | Lists voices |
| `/narratorvoice test 42` | Plays speaker 42 (any number 0 to 903) |
| `/narratorvoice set Notch 42` | Gives a player a specific voice |
| `/narratorvoice reset Notch` | Goes back to the automatic voice |
| `/narratorvoice reload` | Looks for Piper again, for example after installing it with the game open |
| `/narratorvoice on` / `off` | Turns per-player voices on or off |

Settings are in the instance's `config/customnarrator.json`, or in Mod Menu if you installed it. All options are listed in [HOW_TO_INSTALL.md](HOW_TO_INSTALL.md#settings).

## Where the mod looks for Piper

In this order, stopping at the first folder that contains `piper` or `piper/piper`:

1. `piperPath` in the config file, if you set it
2. `~/piper`
3. `~/.local/share/piper`
4. `~/.local/opt/piper`
5. `/opt/piper`
6. `~/Downloads/piper_linux_x86_64` or `~/Downloads/piper_linux_aarch64`
7. `piper` inside the game folder (the instance's `minecraft` folder)
8. `~/Documents/piper`, `~/Desktop/piper`, `~/Downloads/piper`

The voice files can be next to the program, in a `voices` folder next to it, or in a `voices` folder one level up (like `~/piper/voices`).

## Flatpak notes

The Flathub version of Prism runs in a sandbox that can't see most of your home folder, including `~/piper`. Use one of these instead:

- Put Piper in Downloads, which the sandbox can read. After doing step 3, run:
  ```bash
  mv ~/piper ~/Downloads/piper
  ```
- Or put the `piper` folder inside the instance's `minecraft` folder. In Prism: right-click the instance → **Folder**, then open `minecraft`.

Inside Flatpak, `pw-play` usually isn't available, so the mod plays audio through Java instead. `/narratorvoice status` shows `audio: Java | Flatpak`. espeak-ng installed with dnf isn't visible inside the sandbox either, so use Piper.

## Troubleshooting

**Status says NONE**
- Run `ls -l ~/piper/piper/piper ~/piper/voices` and compare it with step 3.
- Both voice files must be there with the exact names. A different voice like `en_US-lessac-medium.onnx` won't work in its place.
- Run `/narratorvoice reload`, wait a few seconds, then `/narratorvoice status`.

**Check the log**

In Prism, click the instance → **Edit** → **Minecraft Log**, and search for `CustomNarrator`. Or from a terminal:

```bash
grep CustomNarrator ~/.local/share/PrismLauncher/instances/*/minecraft/logs/latest.log
```

The log lists every folder the mod checked. If you see `is not Piper text-to-speech`, it found the mouse app or a wrong file. If you see `is not executable`, run `chmod +x ~/piper/piper/piper`.

**Status works but you hear nothing**
- Press `Ctrl + B` until it says **Chat** or **All**.
- Check that `pw-play /tmp/test.wav` from step 3 is audible. If it isn't, the problem is your sound settings, not the mod.
- `/narratorvoice test 42` should play right away. If it plays but chat doesn't, the narrator is set to **System** or **Off**.

**The game won't start**
- Make sure the instance is Minecraft 26.3 with Fabric API, using Java 25.
