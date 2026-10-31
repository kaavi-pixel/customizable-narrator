# Customizable Narrator (Minecraft 26.3)

A client-side Fabric mod that changes how the Narrator reads chat.

| Mode | You hear |
|---|---|
| `PLAYER_AND_MESSAGE` | "Steve says hello" |
| `CUSTOM_NAME_AND_MESSAGE` | "the boss says hello" (custom name per player) |
| `MESSAGE_ONLY` | "hello" |

**Step-by-step guides: [Windows](HOW_TO_INSTALL.md) · [Fedora Linux](HOW_TO_INSTALL_FEDORA.md)** · **Download: [customnarrator-1.4.0.jar](https://github.com/kaavi-pixel/customizable-narrator/raw/main/download/customnarrator-1.4.0.jar)**

## Install

1. Fabric Loader 0.19.5+ for Minecraft 26.3
2. Fabric API 0.161.0+26.3
3. Put `customnarrator-1.4.0.jar` in `mods/`
4. Optional: Cloth Config (26.3.x) and ModMenu (21.0.0) for an in-game settings screen

Set the Narrator to **Chat** or **All** (Ctrl+B).

## Config: `config/customnarrator.json`

```json
{
  "format": "CUSTOM_NAME_AND_MESSAGE",
  "separator": "says",
  "fallbackToRealName": true,
  "aliases": { "Notch": "the boss" }
}
```

- `separator`: a word ("says") gets spaces around it, and punctuation (":") sticks to the name, as in "Steve: hi". Leave it empty to put nothing between the name and the message.
- `fallbackToRealName`: in custom name mode, this decides what happens for players without a custom name. If true, the Narrator reads their real name. If false, it reads only the message.
- Custom name matching ignores upper and lower case.

## Piper voices (recommended, 904 speakers)

Piper is a free, offline voice program that sounds natural. With the LibriTTS-R model, each player gets one of **904 different speakers**. It doesn't need admin rights or Windows Update.

1. Download [piper_windows_amd64.zip](https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip) and extract it to `C:\piper`.
2. Make a folder `C:\piper\voices` and download both of these files into it:
   - [en_US-libritts_r-medium.onnx](https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/libritts_r/medium/en_US-libritts_r-medium.onnx) (78 MB)
   - [en_US-libritts_r-medium.onnx.json](https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/libritts_r/medium/en_US-libritts_r-medium.onnx.json)
3. Start Minecraft (or run `/narratorvoice reload`) and check `/narratorvoice status`.

The mod looks for Piper in `C:\piper`, `C:\piper\piper`, and in `piper` folders under Documents, Desktop and Downloads. If yours is somewhere else, set `piperPath`. Voices are named `speaker 0` to `speaker 903`, and plain numbers work too: `/narratorvoice set Notch 42`.

## Different voice per player

Minecraft's narrator only has one voice, so this mod uses your computer's own speech voices instead:

| System | Uses | Voices |
|---|---|---|
| Windows | System.Speech (built into Windows) | Usually Microsoft David and Zira, plus any extra speech voices you install |
| macOS | `say` | Every voice in System Settings > Accessibility > Spoken Content |
| Linux | `espeak-ng` (`sudo apt install espeak-ng`) | 15 variants such as `en-us+m3`, `en-us+f2`, `en-us+croak` |

By default, each player is automatically given one of your installed voices, and keeps the same one every time. You can also pick a voice yourself:

```
/narratorvoice list                 show installed voices
/narratorvoice set Notch Microsoft Zira
/narratorvoice reset Notch          back to the automatic pick
/narratorvoice test Microsoft David hear a voice
/narratorvoice on | off             per-player voices on or off
/narratorvoice status               which voice engine is in use
/narratorvoice reload               look for Piper / voices again
```

`voiceBackend` can be `AUTO` (use Piper if it's installed, otherwise the computer's voices), `PIPER` or `SYSTEM`. If nothing is found, the mod falls back to Minecraft's normal narrator.

Extra config fields:
```json
"useCustomVoices": true,
"autoAssignVoices": true,
"defaultVoice": "",
"speechRate": 0,
"playerVoices": { "Notch": "speaker 42" },
"voiceBackend": "AUTO",
"piperPath": "",
"piperModel": "en_US-libritts_r-medium"
```

## How it works

`ChatListenerMixin` hooks `ChatListener#narrateChatMessage(ChatType.Bound, Component)`. At that point the sender name and the message are still separate. The mixin builds the line from your settings and passes it to `GameNarrator#sayChatQueued`, so your normal Narrator setting still applies.

## Build

JDK 25, then `./gradlew build`. The output goes to `build/libs/`.

## Linux and Fedora

Version 1.4.0 adds full Fedora support:

- Ctrl+B and the narrator setting work even though Minecraft's own Linux narrator (flite) is missing
- Piper audio plays through PipeWire (`pw-play`), then `paplay`, `aplay`, or Java audio
- Piper is found in `~/piper`, `~/.local/share/piper`, `/opt/piper`, Downloads, or the game folder (for the Prism Flatpak)
- Fedora's unrelated `piper` mouse app is detected and skipped
- espeak-ng, or speech-dispatcher (`spd-say`) as a fallback, when Piper isn't installed

See [HOW_TO_INSTALL_FEDORA.md](HOW_TO_INSTALL_FEDORA.md).

## Build from source

Requires JDK 25.

```
./gradlew build
```

The jar is written to `build/libs/`.
