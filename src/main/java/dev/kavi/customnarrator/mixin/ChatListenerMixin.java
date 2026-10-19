package dev.kavi.customnarrator.mixin;

import dev.kavi.customnarrator.NarrationFormatter;
import dev.kavi.customnarrator.VoiceEngine;
import dev.kavi.customnarrator.config.NarratorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla (26.3): ChatListener#narrateChatMessage(bound, content) calls
 *   minecraft.getNarrator().sayChatQueued(bound.decorateNarration(content))
 * which bakes in "Steve says hello". We hook here instead, while the sender
 * name (bound.name()) and the message (content) are still separate.
 */
@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {

    @Inject(method = "narrateChatMessage", at = @At("HEAD"), cancellable = true)
    private void customnarrator$narrate(ChatType.Bound bound, Component content, CallbackInfo ci) {
        ci.cancel();
        Minecraft mc = Minecraft.getInstance();
        String sender = bound.name().getString();
        String line = NarrationFormatter.format(sender, content.getString());
        if (line.isBlank()) return;

        NarratorConfig cfg = NarratorConfig.get();
        if (cfg.useCustomVoices && VoiceEngine.available()) {
            // Respect the player's Narrator setting (Off / Chat / System / All).
            if (mc.options.narrator().get().shouldNarrateChat()) {
                VoiceEngine.speak(cfg.voiceFor(sender), cfg.speechRate, line);
            }
            return;
        }
        // Fallback: Minecraft's built-in narrator (one voice).
        mc.getNarrator().sayChatQueued(Component.literal(line));
    }
}
