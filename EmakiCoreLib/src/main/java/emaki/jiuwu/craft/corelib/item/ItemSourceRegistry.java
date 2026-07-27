package emaki.jiuwu.craft.corelib.item;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ItemSourceRegistry {

    private static final ItemSourceRegistry SYSTEM = new ItemSourceRegistry();

    private final List<ItemSourceParser> parsers = new CopyOnWriteArrayList<>();
    private final Map<String, RegisteredParser> namedParsers = new ConcurrentHashMap<>();
    private final Map<ItemSourceType, RegisteredShorthandWriter> shorthandWriters = new ConcurrentHashMap<>();
    private final AtomicLong registrationSequence = new AtomicLong();
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
        if (Texts.isBlank(id)) {
            registerParser(parser);
            return;
        }
        registerNamedParser(id, parser);
    }

    public synchronized ParserRegistration registerParserHandle(String id, ItemSourceParser parser) {
        if (Texts.isBlank(id) || parser == null) {
            return new ParserRegistration("", -1L, false);
        }
        String normalizedId = Texts.normalizeId(id);
        long generation = registerNamedParser(normalizedId, parser);
        return new ParserRegistration(normalizedId, generation, true);
    }

    private synchronized long registerNamedParser(String id, ItemSourceParser parser) {
        if (parser == null) {
            return -1L;
        }
        String normalizedId = Texts.normalizeId(id);
        RegisteredParser previous = namedParsers.remove(normalizedId);
        if (previous != null) {
            parsers.remove(previous.parser());
        }
        long generation = registrationSequence.incrementAndGet();
        namedParsers.put(normalizedId, new RegisteredParser(parser, generation));
        parsers.add(0, parser);
        return generation;
    }

    public synchronized void unregisterParser(String id) {
        if (Texts.isBlank(id)) {
            return;
        }
        RegisteredParser registered = namedParsers.remove(Texts.normalizeId(id));
        if (registered != null) {
            parsers.remove(registered.parser());
        }
    }

    public void registerShorthandWriter(ItemSourceType type, Function<ItemSource, String> writer) {
        if (type == null || writer == null) {
            return;
        }
        installShorthandWriter(type, writer);
    }

    public ShorthandWriterRegistration registerShorthandWriterHandle(
            ItemSourceType type,
            Function<ItemSource, String> writer) {
        if (type == null || writer == null) {
            return new ShorthandWriterRegistration(null, -1L, false);
        }
        long generation = installShorthandWriter(type, writer);
        return new ShorthandWriterRegistration(type, generation, true);
    }

    private long installShorthandWriter(ItemSourceType type, Function<ItemSource, String> writer) {
        long generation = registrationSequence.incrementAndGet();
        shorthandWriters.put(type, new RegisteredShorthandWriter(writer, generation));
        return generation;
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
        if (source.getType() == ItemSourceType.EMAKIITEM) {
            String identifier = ItemSourceUtil.normalizeIdentifier(ItemSourceType.EMAKIITEM, source.getIdentifier());
            return Texts.isBlank(identifier) ? null : "emakiitem-" + identifier;
        }
        RegisteredShorthandWriter registered = shorthandWriters.get(source.getType());
        return registered == null ? null : registered.writer().apply(source);
    }

    public void setFallbackParser(ItemSourceParser parser) {
        fallbackParser = parser;
    }

    public ItemSource parseShorthand(String shorthand) {
        if (Texts.isBlank(shorthand)) {
            return null;
        }
        String text = Texts.trim(shorthand);
        String lower = Texts.lower(text);
        if (lower.startsWith("emakiitem-")) {
            return parseReservedShorthand(text, "emakiitem-");
        }
        if (lower.startsWith("ei-")) {
            return parseReservedShorthand(text, "ei-");
        }
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
        parsers.add(prefixParser("ecoitems-", ItemSourceType.ECOITEMS));
        parsers.add(prefixParser("eci-", ItemSourceType.ECOITEMS));
        parsers.add(prefixParser("craftengine-", ItemSourceType.CRAFTENGINE));
        parsers.add(prefixParser("ce-", ItemSourceType.CRAFTENGINE));
        parsers.add(prefixParser("minecraft-", ItemSourceType.VANILLA));
        parsers.add(prefixParser("mc-", ItemSourceType.VANILLA));
        parsers.add(prefixParser("v-", ItemSourceType.VANILLA));
    }

    private ItemSource parseReservedShorthand(String shorthand, String prefix) {
        String identifier = shorthand.substring(prefix.length());
        if (Texts.isBlank(identifier)) {
            return null;
        }
        String normalized = ItemSourceUtil.normalizeIdentifier(ItemSourceType.EMAKIITEM, identifier);
        return Texts.isBlank(normalized) ? null : new ItemSource(ItemSourceType.EMAKIITEM, normalized);
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

    private synchronized boolean unregisterParserIfMatches(String id, long generation) {
        RegisteredParser registered = namedParsers.get(id);
        if (registered == null || registered.generation() != generation) {
            return false;
        }
        namedParsers.remove(id);
        parsers.remove(registered.parser());
        return true;
    }

    private boolean unregisterShorthandWriterIfMatches(ItemSourceType type, long generation) {
        AtomicBoolean removed = new AtomicBoolean();
        shorthandWriters.computeIfPresent(type, (_, registered) -> {
            if (registered.generation() != generation) {
                return registered;
            }
            removed.set(true);
            return null;
        });
        return removed.get();
    }

    public final class ParserRegistration implements AutoCloseable {

        private final String id;
        private final long generation;
        private final boolean registered;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ParserRegistration(String id, long generation, boolean registered) {
            this.id = id;
            this.generation = generation;
            this.registered = registered;
        }

        public boolean registered() {
            return registered;
        }

        public boolean unregister() {
            return registered && closed.compareAndSet(false, true) && unregisterParserIfMatches(id, generation);
        }

        @Override
        public void close() {
            unregister();
        }
    }

    public final class ShorthandWriterRegistration implements AutoCloseable {

        private final ItemSourceType type;
        private final long generation;
        private final boolean registered;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ShorthandWriterRegistration(ItemSourceType type, long generation, boolean registered) {
            this.type = type;
            this.generation = generation;
            this.registered = registered;
        }

        public boolean registered() {
            return registered;
        }

        public boolean unregister() {
            return registered && closed.compareAndSet(false, true)
                    && unregisterShorthandWriterIfMatches(type, generation);
        }

        @Override
        public void close() {
            unregister();
        }
    }

    private record RegisteredParser(ItemSourceParser parser, long generation) {
    }

    private record RegisteredShorthandWriter(Function<ItemSource, String> writer, long generation) {
    }
}
