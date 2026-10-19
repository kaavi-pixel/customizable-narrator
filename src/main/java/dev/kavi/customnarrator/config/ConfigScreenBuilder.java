package dev.kavi.customnarrator.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigScreenBuilder {
    private ConfigScreenBuilder() {}

    public static Screen build(Screen parent) {
        NarratorConfig cfg = NarratorConfig.get();
        ConfigBuilder b = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("customnarrator.config.title"))
                .setSavingRunnable(() -> {
                    NarratorConfig.save();
                    dev.kavi.customnarrator.VoiceEngine.reload(); // pick up engine / Piper changes
                });
        ConfigEntryBuilder e = b.entryBuilder();

        ConfigCategory general = b.getOrCreateCategory(Component.translatable("customnarrator.config.category.general"));
        general.addEntry(e.startEnumSelector(Component.translatable("customnarrator.config.format"),
                        NarratorConfig.Format.class, cfg.format)
                .setDefaultValue(NarratorConfig.Format.PLAYER_AND_MESSAGE)
                .setEnumNameProvider(v -> Component.translatable("customnarrator.config.format." + v.name()))
                .setTooltip(Component.translatable("customnarrator.config.format.tooltip"))
                .setSaveConsumer(v -> cfg.format = v)
                .build());
        general.addEntry(e.startStrField(Component.translatable("customnarrator.config.separator"), cfg.separator)
                .setDefaultValue("says")
                .setTooltip(Component.translatable("customnarrator.config.separator.tooltip"))
                .setSaveConsumer(v -> cfg.separator = v)
                .build());
        general.addEntry(e.startBooleanToggle(Component.translatable("customnarrator.config.fallbackToReal"), cfg.fallbackToRealName)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("customnarrator.config.fallbackToReal.tooltip"))
                .setSaveConsumer(v -> cfg.fallbackToRealName = v)
                .build());

        ConfigCategory names = b.getOrCreateCategory(Component.translatable("customnarrator.config.category.aliases"));
        List<String> lines = new ArrayList<>();
        cfg.aliases.forEach((k, v) -> lines.add(k + "=" + v));
        names.addEntry(e.startStrList(Component.translatable("customnarrator.config.aliases"), lines)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.translatable("customnarrator.config.aliases.tooltip"))
                .setSaveConsumer(list -> cfg.aliases = parse(list))
                .build());
        ConfigCategory voices = b.getOrCreateCategory(Component.translatable("customnarrator.config.category.voices"));
        voices.addEntry(e.startBooleanToggle(Component.translatable("customnarrator.config.useCustomVoices"), cfg.useCustomVoices)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("customnarrator.config.useCustomVoices.tooltip"))
                .setSaveConsumer(v -> cfg.useCustomVoices = v)
                .build());
        voices.addEntry(e.startEnumSelector(Component.translatable("customnarrator.config.voiceBackend"),
                        NarratorConfig.VoiceBackend.class, cfg.voiceBackend)
                .setDefaultValue(NarratorConfig.VoiceBackend.AUTO)
                .setTooltip(Component.translatable("customnarrator.config.voiceBackend.tooltip"))
                .setSaveConsumer(v -> cfg.voiceBackend = v)
                .build());
        voices.addEntry(e.startStrField(Component.translatable("customnarrator.config.piperPath"), cfg.piperPath)
                .setDefaultValue("")
                .setTooltip(Component.translatable("customnarrator.config.piperPath.tooltip"))
                .setSaveConsumer(v -> cfg.piperPath = v.trim())
                .build());
        voices.addEntry(e.startStrField(Component.translatable("customnarrator.config.piperModel"), cfg.piperModel)
                .setDefaultValue("en_US-libritts_r-medium")
                .setTooltip(Component.translatable("customnarrator.config.piperModel.tooltip"))
                .setSaveConsumer(v -> cfg.piperModel = v.trim().isEmpty() ? "en_US-libritts_r-medium" : v.trim())
                .build());
        voices.addEntry(e.startBooleanToggle(Component.translatable("customnarrator.config.autoAssignVoices"), cfg.autoAssignVoices)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("customnarrator.config.autoAssignVoices.tooltip"))
                .setSaveConsumer(v -> cfg.autoAssignVoices = v)
                .build());
        voices.addEntry(e.startStrField(Component.translatable("customnarrator.config.defaultVoice"), cfg.defaultVoice)
                .setDefaultValue("")
                .setTooltip(Component.translatable("customnarrator.config.defaultVoice.tooltip"))
                .setSaveConsumer(v -> cfg.defaultVoice = v.trim())
                .build());
        voices.addEntry(e.startIntSlider(Component.translatable("customnarrator.config.speechRate"), cfg.speechRate, -10, 10)
                .setDefaultValue(0)
                .setSaveConsumer(v -> cfg.speechRate = v)
                .build());
        List<String> voiceLines = new ArrayList<>();
        cfg.playerVoices.forEach((k, v) -> voiceLines.add(k + "=" + v));
        voices.addEntry(e.startStrList(Component.translatable("customnarrator.config.playerVoices"), voiceLines)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.translatable("customnarrator.config.playerVoices.tooltip"))
                .setSaveConsumer(list -> cfg.playerVoices = parse(list))
                .build());

        return b.build();
    }

    /** Turns "Name=Value" lines into a map, skipping bad lines. */
    private static Map<String, String> parse(List<String> list) {
        Map<String, String> next = new LinkedHashMap<>();
        for (String line : list) {
            int eq = line == null ? -1 : line.indexOf('=');
            if (eq <= 0) continue;
            String k = line.substring(0, eq).trim(), v = line.substring(eq + 1).trim();
            if (!k.isEmpty() && !v.isEmpty()) next.put(k, v);
        }
        return next;
    }
}
