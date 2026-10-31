package dev.kavi.customnarrator.mixin;

import com.mojang.text2speech.Narrator;
import dev.kavi.customnarrator.VoiceEngine;
import dev.kavi.customnarrator.config.NarratorConfig;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On Linux, Minecraft's own narrator needs the flite library. Without it, the game treats the
 * narrator as "not available": Ctrl+B does nothing and the setting is greyed out, so chat could
 * never be narrated even though Piper or espeak-ng works.
 *
 * When this mod's voices are ready, we report the narrator as available so the setting works,
 * and show the normal on/off toast instead of "Narrator not available". Minecraft's own speech
 * is untouched (it stays silent if flite is missing). Chat speech comes from this mod.
 */
@Mixin(GameNarrator.class)
public abstract class GameNarratorMixin {

    @Shadow @Final private Narrator narrator;

    private boolean customnarrator$takeOver() {
        return !narrator.active() && NarratorConfig.get().useCustomVoices && VoiceEngine.available();
    }

    @Inject(method = "isActive", at = @At("RETURN"), cancellable = true)
    private void customnarrator$isActive(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && customnarrator$takeOver()) cir.setReturnValue(true);
    }

    @Inject(method = "updateNarratorStatus", at = @At("HEAD"), cancellable = true)
    private void customnarrator$toast(NarratorStatus status, CallbackInfo ci) {
        if (!customnarrator$takeOver()) return;
        ci.cancel();
        Minecraft mc = Minecraft.getInstance();
        if (status == NarratorStatus.OFF) {
            SystemToast.addOrUpdate(mc.gui.toastManager(), SystemToast.SystemToastId.NARRATOR_TOGGLE,
                    Component.translatable("narrator.toast.disabled"), null);
        } else {
            SystemToast.addOrUpdate(mc.gui.toastManager(), SystemToast.SystemToastId.NARRATOR_TOGGLE,
                    Component.translatable("narrator.toast.enabled"), status.getName());
        }
    }
}
