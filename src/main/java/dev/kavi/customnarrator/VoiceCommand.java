package dev.kavi.customnarrator;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kavi.customnarrator.config.NarratorConfig;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * /narratorvoice list                     - show installed voices
 * /narratorvoice set <player> <voice>     - give a player a voice
 * /narratorvoice reset <player>           - go back to auto / default voice
 * /narratorvoice test <voice>             - hear a voice
 * /narratorvoice on | off                 - turn per-player voices on or off
 * /narratorvoice status                   - what voice engine is in use
 * /narratorvoice reload                   - look for Piper / voices again
 *
 * With Piper's LibriTTS model, voices are "speaker 0" .. "speaker 903" (or just the number).
 */
public final class VoiceCommand {
    private VoiceCommand() {}

    private static final SuggestionProvider<FabricClientCommandSource> PLAYERS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        ClientPacketListener conn = ctx.getSource().getClient().getConnection();
        if (conn != null) for (PlayerInfo p : conn.getOnlinePlayers()) names.add(p.getProfile().name());
        return SharedSuggestionProvider.suggest(names, b);
    };

    private static final SuggestionProvider<FabricClientCommandSource> VOICES =
            (ctx, b) -> SharedSuggestionProvider.suggest(VoiceEngine.voices(), b);

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(literal("narratorvoice")
                .then(literal("list").executes(VoiceCommand::list))
                .then(literal("status").executes(VoiceCommand::status))
                .then(literal("reload").executes(VoiceCommand::reload))
                .then(literal("on").executes(c -> toggle(c, true)))
                .then(literal("off").executes(c -> toggle(c, false)))
                .then(literal("test")
                        .then(argument("voice", StringArgumentType.greedyString()).suggests(VOICES)
                                .executes(VoiceCommand::test)))
                .then(literal("reset")
                        .then(argument("player", StringArgumentType.word()).suggests(PLAYERS)
                                .executes(VoiceCommand::reset)))
                .then(literal("set")
                        .then(argument("player", StringArgumentType.word()).suggests(PLAYERS)
                                .then(argument("voice", StringArgumentType.greedyString()).suggests(VOICES)
                                        .executes(VoiceCommand::set)))));
    }

    private static boolean ready(FabricClientCommandSource s) {
        if (VoiceEngine.available()) return true;
        s.sendError(Component.literal(switch (VoiceEngine.backend()) {
            case DETECTING -> "Voices are still loading, try again in a second.";
            default -> "No voices found. Put Piper in C:\\piper with en_US-libritts_r-medium in C:\\piper\\voices, then /narratorvoice reload";
        }));
        return false;
    }

    private static int list(CommandContext<FabricClientCommandSource> c) {
        FabricClientCommandSource s = c.getSource();
        if (!ready(s)) return 0;
        List<String> all = VoiceEngine.voices();
        if (VoiceEngine.backend() == VoiceEngine.Backend.PIPER) {
            s.sendFeedback(Component.literal("Piper has " + all.size() + " speakers: "
                    + VoiceEngine.PIPER_PREFIX + "0 to " + VoiceEngine.PIPER_PREFIX + (all.size() - 1)
                    + ". Use the number, e.g. /narratorvoice test 42"));
            NarratorConfig cfg = NarratorConfig.get();
            if (!cfg.playerVoices.isEmpty()) {
                s.sendFeedback(Component.literal("Chosen voices:"));
                cfg.playerVoices.forEach((p, v) -> s.sendFeedback(Component.literal(" - " + p + ": " + v)));
            }
            return 1;
        }
        s.sendFeedback(Component.literal("Installed voices (" + VoiceEngine.backend() + "):"));
        for (String v : all) s.sendFeedback(Component.literal(" - " + v));
        return 1;
    }

    private static int status(CommandContext<FabricClientCommandSource> c) {
        c.getSource().sendFeedback(Component.literal("Voice engine: " + (VoiceEngine.backend() == VoiceEngine.Backend.DETECTING
                ? "still loading..." : VoiceEngine.statusInfo())));
        return 1;
    }

    private static int reload(CommandContext<FabricClientCommandSource> c) {
        NarratorConfig.load();
        VoiceEngine.reload();
        c.getSource().sendFeedback(Component.literal("Reloading voices... check /narratorvoice status in a few seconds"));
        return 1;
    }

    private static int toggle(CommandContext<FabricClientCommandSource> c, boolean on) {
        NarratorConfig.get().useCustomVoices = on;
        NarratorConfig.save();
        c.getSource().sendFeedback(Component.literal("Per-player voices " + (on ? "ON" : "OFF")));
        return 1;
    }

    private static int test(CommandContext<FabricClientCommandSource> c) {
        FabricClientCommandSource s = c.getSource();
        if (!ready(s)) return 0;
        String voice = StringArgumentType.getString(c, "voice");
        String match = VoiceEngine.matchVoice(voice);
        String use = match != null ? match : voice; // espeak accepts voice names we don't list
        VoiceEngine.speak(use, NarratorConfig.get().speechRate, "Hi, this is " + use + ". How do I sound?");
        return 1;
    }

    private static int set(CommandContext<FabricClientCommandSource> c) {
        FabricClientCommandSource s = c.getSource();
        if (!ready(s)) return 0;
        String player = StringArgumentType.getString(c, "player");
        String voice = StringArgumentType.getString(c, "voice");
        String match = VoiceEngine.matchVoice(voice);
        if (match == null && VoiceEngine.backend() != VoiceEngine.Backend.ESPEAK) {
            s.sendError(Component.literal("Voice not installed: " + voice + ". Use /narratorvoice list"));
            return 0;
        }
        String use = match != null ? match : voice;
        NarratorConfig cfg = NarratorConfig.get();
        NarratorConfig.removeIgnoreCase(cfg.playerVoices, player);
        cfg.playerVoices.put(player, use);
        NarratorConfig.save();
        s.sendFeedback(Component.literal(player + " will now be read with " + use));
        VoiceEngine.speak(use, cfg.speechRate, player + " now sounds like this");
        return 1;
    }

    private static int reset(CommandContext<FabricClientCommandSource> c) {
        String player = StringArgumentType.getString(c, "player");
        NarratorConfig cfg = NarratorConfig.get();
        NarratorConfig.removeIgnoreCase(cfg.playerVoices, player);
        NarratorConfig.save();
        c.getSource().sendFeedback(Component.literal(player + " is back to " +
                (cfg.autoAssignVoices ? "an auto-picked voice" : "the default voice")));
        return 1;
    }
}
