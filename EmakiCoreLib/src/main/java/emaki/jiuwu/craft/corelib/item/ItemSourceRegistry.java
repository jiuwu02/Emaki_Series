package emaki.jiuwu.craft.corelib.item;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ItemSourceRegistry {

    private static final ItemSourceRegistry SYSTEM = new ItemSourceRegistry();

    private final List<ItemSourceParser> parsers = new CopyOnWriteArrayList<>();
    private final Map<String, ItemSourceParser> namedParsers = new ConcurrentHashMap<>();
    private final Map<ItemSourceType, Function<ItemSource, String>> shorthandWriters = new ConcurrentHashMap<>();
    private volatile ItemSourceParser fallbackParser = ItemSourceUtil::parseVanillaShorthand;

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

    public void registerParser(String id, ItemSourceParser parser) {
        if (Texts.isBlank(id) || parser == null) {
            registerParser(parser);
            return;
        }
        String normalizedId = Texts.normalizeId(id);
        unregisterParser(normalizedId);
        namedParsers.put(normalizedId, parser);
        parsers.add(0, parser);
    }

    public void unregisterParser(String id) {
        if (Texts.isBlank(id)) {
            return;
        }
        ItemSourceParser parser = namedParsers.remove(Texts.normalizeId(id));
        if (parser != null) {
            parsers.remove(parser);
        }
    }

    public void registerShorthandWriter(ItemSourceType type, Function<ItemSource, String> writer) {
        if (type == null || writer == null) {
            return;
        }
        shorthandWriters.put(type, writer);
    }

    public void unregisterShorthandWriter(ItemSourceType type) {
        if (type != null) {
            shorthandWriters.remove(type);
        }
    }

    public String toShorthand(ItemSource source) {
        if (source == null || source.getType() == null) {
            return null;
        }
        Function<ItemSource, String> writer = shorthandWriters.get(source.getType());
        return writer == null ? null : writer.apply(source);
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
        parsers.add(prefixParser("mmoitems-", ItemSourceType.MMOITEMS));
        parsers.add(prefixParser("mi-", ItemSourceType.MMOITEMS));
        parsers.add(prefixParser("itemsadder-", ItemSourceType.ITEMSADDER));
        parsers.add(prefixParser("ia-", ItemSourceType.ITEMSADDER));
        parsers.add(prefixParser("neigeitems-", ItemSourceType.NEIGEITEMS));
        parsers.add(prefixParser("ni-", ItemSourceType.NEIGEITEMS));
        parsers.add(prefixParser("nexo-", ItemSourceType.NEXO));
        parsers.add(prefixParser("no-", ItemSourceType.NEXO));
        parsers.add(prefixParser("oraxen-", ItemSourceType.ORAXEN));
        parsers.add(prefixParser("ox-", ItemSourceType.ORAXEN));
        parsers.add(prefixParser("craftengine-", ItemSourceType.CRAFTENGINE));
        parsers.add(prefixParser("ce-", ItemSourceType.CRAFTENGINE));
        parsers.add(prefixParser("minecraft-", ItemSourceType.VANILLA));
        parsers.add(prefixParser("mc-", ItemSourceType.VANILLA));
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
            if (Texts.isBlank(identifier)) {
                return null;
            }
            String normalized = ItemSourceUtil.normalizeIdentifier(type, identifier);
            return Texts.isBlank(normalized) ? null : new ItemSource(type, normalized);
        };
    }
}
