package emaki.jiuwu.craft.corelib.text;

import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class MiniMessages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private MiniMessages() {
    }

    public static Component parse(String text) {
        if (Texts.isBlank(text)) {
            return Component.empty();
        }
        try {
            return MINI_MESSAGE.deserialize(text);
        } catch (Exception _) {
            return Component.text(Texts.toStringSafe(text));
        }
    }

    public static Component read(String text) {
        if (Texts.isBlank(text)) {
            return Component.empty();
        }
        String normalized = Texts.toStringSafe(text);
        if (normalized.indexOf(LegacyComponentSerializer.SECTION_CHAR) >= 0) {
            try {
                return LEGACY.deserialize(normalized);
            } catch (Exception _) {
            }
        }
        return parse(normalized);
    }

    public static String serialize(Component component) {
        if (component == null) {
            return "";
        }
        try {
            return MINI_MESSAGE.serialize(component);
        } catch (Exception _) {
            return plain(component);
        }
    }

    public static String plain(Component component) {
        if (component == null) {
            return "";
        }
        try {
            return PLAIN.serialize(component);
        } catch (Exception _) {
            return "";
        }
    }

    public static String legacy(Component component) {
        if (component == null) {
            return "";
        }
        try {
            return LEGACY.serialize(component);
        } catch (Exception _) {
            return plain(component);
        }
    }

    public static String toMiniMessage(Object value) {
        if (value instanceof Component component) {
            return serialize(component);
        }
        return Texts.toStringSafe(value);
    }

    public static String plainText(String text) {
        if (Texts.isBlank(text)) {
            return "";
        }
        return plain(read(text));
    }

    public static String plainText(Object value) {
        if (value instanceof Component component) {
            return plain(component);
        }
        return plainText(Texts.toStringSafe(value));
    }

    public static String escape(String text) {
        if (Texts.isBlank(text)) {
            return "";
        }
        return MINI_MESSAGE.escapeTags(Texts.toStringSafe(text));
    }

    public static String withHoverText(String content, String hoverText) {
        if (Texts.isBlank(content)) {
            return "";
        }
        Component rendered = parse(content);
        if (Texts.isBlank(hoverText)) {
            return serialize(rendered);
        }
        return serialize(rendered.hoverEvent(HoverEvent.showText(parse(hoverText))));
    }

    public static MiniMessage miniMessage() {
        return MINI_MESSAGE;
    }
}
