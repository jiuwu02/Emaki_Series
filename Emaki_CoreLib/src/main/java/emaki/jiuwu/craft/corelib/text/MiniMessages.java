package emaki.jiuwu.craft.corelib.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class MiniMessages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private MiniMessages() {
    }

    public static Component parse(String text) {
        if (Texts.isBlank(text)) {
            return Component.empty();
        }
        try {
            return MINI_MESSAGE.deserialize(text);
        } catch (Exception ignored) {
            return Component.text(Texts.toStringSafe(text));
        }
    }

    public static String serialize(Component component) {
        if (component == null) {
            return "";
        }
        try {
            return MINI_MESSAGE.serialize(component);
        } catch (Exception ignored) {
            return plain(component);
        }
    }

    public static String plain(Component component) {
        if (component == null) {
            return "";
        }
        try {
            return PLAIN.serialize(component);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static MiniMessage miniMessage() {
        return MINI_MESSAGE;
    }
}
