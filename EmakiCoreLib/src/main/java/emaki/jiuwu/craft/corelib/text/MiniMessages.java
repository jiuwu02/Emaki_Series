package emaki.jiuwu.craft.corelib.text;

import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class MiniMessages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private static volatile boolean defaultNoItalic;

    private MiniMessages() {
    }

    /**
     * 配置 MiniMessage 解析结果是否默认取消斜体。
     * 由 CoreLib 在启用与重载时依据 {@code minimessage.default_no_italic} 调用。
     */
    public static void configureDefaultNoItalic(boolean enabled) {
        defaultNoItalic = enabled;
    }

    public static boolean defaultNoItalic() {
        return defaultNoItalic;
    }

    public static Component parse(String text) {
        if (Texts.isBlank(text)) {
            return Component.empty();
        }
        try {
            return applyDefaults(MINI_MESSAGE.deserialize(text));
        } catch (Exception _) {
            return applyDefaults(Component.text(Texts.toStringSafe(text)));
        }
    }

    /**
     * 套用全局文本默认值。仅在未显式声明时生效，文本内写出的 {@code <i>} 不会被覆盖。
     */
    private static Component applyDefaults(Component component) {
        if (component == null) {
            return Component.empty();
        }
        if (!defaultNoItalic) {
            return component;
        }
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Object componentObject(String text) {
        return parse(text);
    }

    public static Component read(String text) {
        if (Texts.isBlank(text)) {
            return Component.empty();
        }
        return parse(Texts.toStringSafe(text));
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

    public static String legacyText(String text) {
        if (Texts.isBlank(text)) {
            return "";
        }
        return legacy(read(text));
    }

    public static Component legacyRead(String text) {
        if (Texts.isBlank(text)) {
            return Component.empty();
        }
        try {
            return applyDefaults(LEGACY.deserialize(Texts.toStringSafe(text)));
        } catch (Exception _) {
            return applyDefaults(Component.text(Texts.toStringSafe(text)));
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

    /**
     * 返回底层 MiniMessage 实例。直接用它反序列化不会套用
     * {@code minimessage.default_no_italic} 等全局默认值，需要默认值时请使用 {@link #parse(String)}。
     */
    public static MiniMessage miniMessage() {
        return MINI_MESSAGE;
    }
}
