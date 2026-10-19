package dev.kavi.customnarrator;

import dev.kavi.customnarrator.config.NarratorConfig;

/** Builds the line the Narrator should say from the sender name and message body. */
public final class NarrationFormatter {
    private NarrationFormatter() {}

    public static String format(String senderName, String message) {
        NarratorConfig cfg = NarratorConfig.get();
        if (message == null) message = "";
        return switch (cfg.format) {
            case MESSAGE_ONLY -> message;
            case PLAYER_AND_MESSAGE -> join(senderName, cfg.separator, message);
            case CUSTOM_NAME_AND_MESSAGE -> {
                String alias = cfg.aliasFor(senderName);
                if (alias != null) yield join(alias, cfg.separator, message);
                yield cfg.fallbackToRealName ? join(senderName, cfg.separator, message) : message;
            }
        };
    }

    private static String join(String name, String sep, String message) {
        if (name == null || name.isBlank()) return message;
        if (sep == null || sep.isBlank()) return name + " " + message;
        String s = sep.strip();
        // punctuation like ":" sticks to the name ("Steve: hi"), words get spaces ("Steve says hi")
        boolean punct = !Character.isLetterOrDigit(s.charAt(0));
        return punct ? name + s + " " + message : name + " " + s + " " + message;
    }
}
