# How to Install Customizable Narrator

This guide is for **Minecraft Java Edition 26.3** on Windows. Mac and Linux notes are at the end.

## What you need

| Thing | Version | Where to get it |
|---|---|---|
| Minecraft Java Edition | 26.3 | Minecraft Launcher |
| Fabric Loader | 0.19.5 or newer | [fabricmc.net/use/installer](https://fabricmc.net/use/installer/) |
| Fabric API | 0.161.0+26.3 or newer | [Modrinth](https://modrinth.com/mod/fabric-api/versions?g=26.3) |
| Customizable Narrator | 1.3.0 | [customnarrator-1.3.0.jar](https://github.com/kaavi-pixel/customizable-narrator/raw/main/download/customnarrator-1.3.0.jar) |
| Piper + LibriTTS voice (optional) | see step 3 | for 904 different voices |
| Cloth Config + ModMenu (optional) | 26.3.x / 21.0.0 | for an in-game settings screen |

## 1. Install Fabric

1. Download and run the [Fabric Installer](https://fabricmc.net/use/installer/).
2. Pick Minecraft version **26.3** and click **Install**.
3. Open the Minecraft Launcher. A new **fabric-loader-26.3** profile appears.

## 2. Install the mod

1. Press `Win + R`, type `%appdata%\.minecraft\mods`, and press Enter. Create the `mods` folder if it doesn't exist.
2. Put these files in it:
   - [customnarrator-1.3.0.jar](https://github.com/kaavi-pixel/customizable-narrator/raw/main/download/customnarrator-1.3.0.jar)
   - `fabric-api-0.161.0+26.3.jar` (or newer for 26.3)
   - Optional: Cloth Config and ModMenu for the settings screen

At this point the mod already works with your computer's built-in voices (on Windows, usually Microsoft David and Zira). Step 3 adds 904 voices.

## 3. Install Piper and the LibriTTS voice (recommended)

[Piper](https://github.com/rhasspy/piper) is a free, offline voice program. With the LibriTTS-R model, every player gets one of 904 different voices. It doesn't need admin rights.

### 3a. Download Piper

1. Download [piper_windows_amd64.zip](https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip).
2. Right-click it, choose **Extract All**, and extract to `C:\`.

You should end up with `C:\piper\piper.exe`. If you instead get `C:\piper\piper\piper.exe`, that works too.

### 3b. Download the LibriTTS voice

The voice is **two files**, and you need both. Open PowerShell (the normal one, not admin) and paste:

```powershell
mkdir C:\piper\voices -Force
cd C:\piper\voices
curl.exe -L -o en_US-libritts_r-medium.onnx https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/libritts_r/medium/en_US-libritts_r-medium.onnx
curl.exe -L -o en_US-libritts_r-medium.onnx.json https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/libritts_r/medium/en_US-libritts_r-medium.onnx.json
dir
```

Check that `dir` shows both files with these sizes:

| File | Size |
|---|---|
| `en_US-libritts_r-medium.onnx` | 78,580,914 bytes |
| `en_US-libritts_r-medium.onnx.json` | 20,123 bytes |

If the `.onnx` file is much smaller, the download stopped early. Run the `curl.exe` line again.

### 3c. Test Piper (optional)

```powershell
cd C:\piper
echo "Hello, I am speaker forty two" | .\piper.exe -m voices\en_US-libritts_r-medium.onnx --speaker 42 -f test.wav
start test.wav
```

If `piper.exe` is in `C:\piper\piper\`, use `.\piper\piper.exe` instead.

## 4. Turn it on in Minecraft

1. Launch the **fabric-loader-26.3** profile.
2. Press `Ctrl + B` until the Narrator is set to **Chat** or **All**.
3. Type `/narratorvoice status`. It should say:
   `Piper, model en_US-libritts_r-medium.onnx, 904 speakers`
4. Type `/narratorvoice test 42` to hear a voice.

## Commands

| Command | What it does |
|---|---|
| `/narratorvoice status` | Shows which voice engine is in use |
| `/narratorvoice list` | Lists voices |
| `/narratorvoice test 42` | Plays speaker 42 (any number 0 to 903) |
| `/narratorvoice set Notch 42` | Gives a player a specific voice |
| `/narratorvoice reset Notch` | Goes back to the automatic voice |
| `/narratorvoice reload` | Looks for Piper again, for example after installing it with the game open |
| `/narratorvoice on` / `off` | Turns per-player voices on or off |

Every player automatically gets a voice, and it stays the same every time they talk.

## Settings

Settings are in `%appdata%\.minecraft\config\customnarrator.json`, or in ModMenu → Customizable Narrator if you installed Cloth Config and ModMenu.

```json
{
  "format": "PLAYER_AND_MESSAGE",
  "separator": "says",
  "fallbackToRealName": true,
  "aliases": { "Notch": "the boss" },
  "useCustomVoices": true,
  "autoAssignVoices": true,
  "defaultVoice": "",
  "speechRate": 0,
  "playerVoices": { "Notch": "speaker 42" },
  "voiceBackend": "AUTO",
  "piperPath": "",
  "piperModel": "en_US-libritts_r-medium"
}
```

| Setting | Options |
|---|---|
| `format` | `PLAYER_AND_MESSAGE` ("Steve says hi"), `CUSTOM_NAME_AND_MESSAGE` (uses `aliases`), `MESSAGE_ONLY` ("hi") |
| `separator` | Word or symbol between name and message, e.g. `says` or `:` |
| `speechRate` | -10 (slow) to 10 (fast) |
| `voiceBackend` | `AUTO` (Piper if found, otherwise system voices), `PIPER`, `SYSTEM` |
| `piperPath` | Folder with `piper.exe`, if it isn't in `C:\piper` |

## Troubleshooting

**`/narratorvoice status` says WINDOWS instead of Piper**
- Make sure **both** LibriTTS files are in `C:\piper\voices` and the `.onnx` is 78,580,914 bytes. A different voice file like `en_US-lessac-medium.onnx` won't work in its place.
- Check that the file names are exact. Browsers sometimes rename files to things like `en_US-libritts_r-medium.onnx (1).json`.
- Run `/narratorvoice reload`, wait a few seconds, then `/narratorvoice status`.
- Open `%appdata%\.minecraft\logs\latest.log` and search for `[CustomNarrator]`. It lists where the mod looked for Piper.

**Nothing is read out loud**
- Press `Ctrl + B` until the Narrator says **Chat** or **All**.
- Run `/narratorvoice test 42`. If that is silent too, check the log as above.

**The game won't start**
- Make sure you're on Minecraft 26.3 with Fabric API installed. The mod needs Java 25, which the Minecraft Launcher includes for 26.3.

## Mac and Linux

The mod itself installs the same way. Your `mods` folder is:
- Mac: `~/Library/Application Support/minecraft/mods`
- Linux: `~/.minecraft/mods`

For Piper, download the [Mac](https://github.com/rhasspy/piper/releases/tag/2023.11.14-2) or [Linux](https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_x86_64.tar.gz) build and put it in `~/piper`, with the voice files in `~/piper/voices`. Without Piper, the mod uses `say` on Mac or `espeak-ng` on Linux (`sudo apt install espeak-ng`).
