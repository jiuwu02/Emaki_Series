package emaki.jiuwu.craft.corelib.item;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry for shorthand {@link ItemSource} parsers.
 *
 * <p>Custom parsers are evaluated before the built-in parsers so projects can
 * extend CoreLib without forking the default implementation.
 */
public final class ItemSourceRegistry {

    private static final ItemSourceRegistry SYSTEM = new ItemSourceRegistry();

    private final List<ItemSourceParser> parsers = new CopyOnWriteArrayList<>();
    private volatile ItemSourceParser fallbackParser = shorthand ->
        Texts.isBlank(shorthand) ? null : new ItemSource(ItemSourceType.VANILLA, Texts.trim(shorthand));

    private ItemSourceRegistry() {
        registerBuiltinParsers();
    }

    public static ItemSourceRegistry system() {
        return SYSTEM;
    }

    public void registerParser(ItemSourceParser parser) {
        if (parser != null) {
            parsers.add(0, parser);
        }
    }

    public void setFallbackParser(ItemSourceParser parser) {
        fallbackParser = parser;
    }

    public ItemSource parseShorthand(String shorthand) {
        if (Texts.isBlank(shorthand)) {
            return null;
        }
        String text = Texts.trim(shorthand);
        for (ItemSourceParser parser : parsers) {
            ItemSource parsed = parser.parse(text);
            if (parsed != null) {
                return parsed;
            }
        }
        ItemSourceParser parser = fallbackParser;
        return parser == null ? null : parser.parse(text);
    }

    private void registerBuiltinParsers() {
        parsers.add(prefixParser("neigeitems-", ItemSourceType.NEIGEITEMS));
        parsers.add(prefixParser("ni-", ItemSourceType.NEIGEITEMS));
        parsers.add(prefixParser("craftengine-", ItemSourceType.CRAFTENGINE));
        parsers.add(prefixParser("ce-", ItemSourceType.CRAFTENGINE));
        parsers.add(prefixParser("minecraft-", ItemSourceType.VANILLA));
        parsers.add(prefixParser("v-", ItemSourceType.VANILLA));
    }

    private ItemSourceParser prefixParser(String prefix, ItemSourceType type) {
        return shorthand -> {
            if (Texts.isBlank(shorthand)) {
                return null;
            }
            String text = Texts.trim(shorthand);
            if (!Texts.lower(text).startsWith(prefix)) {
                return null;
            }
            String identifier = text.substring(prefix.length());
            return Texts.isBlank(identifier) ? null : new ItemSource(type, identifier);
        };
    }
}
