package dev.kavi.customnarrator;

import dev.kavi.customnarrator.config.NarratorConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CustomNarratorClient implements ClientModInitializer {
    public static final String MOD_ID = "customnarrator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        NarratorConfig.load();
        VoiceEngine.init();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> VoiceCommand.register(dispatcher));
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> VoiceEngine.shutdown());
        LOGGER.info("[CustomNarrator] loaded, format = {}", NarratorConfig.get().format);
    }
}
