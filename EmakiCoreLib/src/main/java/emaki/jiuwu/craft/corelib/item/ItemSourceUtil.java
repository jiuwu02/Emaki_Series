package emaki.jiuwu.craft.corelib.item;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ItemSourceUtil {

    private ItemSourceUtil() {
    }

    public static ItemSource parse(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof ItemSource itemSource) {
            return itemSource;
        }
        if (raw instanceof String text) {
            return parseShorthand(text);
        }
        String item = ConfigNodes.string(raw, "item", null);
        if (Texts.isNotBlank(item)) {
            return parseShorthand(item);
        }
        Object nestedSource = ConfigNodes.get(raw, "source");
        if (nestedSource != null && nestedSource != raw) {
            ItemSource source = parse(nestedSource);
            if (source != null) {
                return source;
            }
        }
        String type = ConfigNodes.string(raw, "type", null);
        String identifier = ConfigNodes.string(raw, "identifier", null);
        if (Texts.isBlank(type) || Texts.isBlank(identifier)) {
            return null;
        }
        ItemSourceType sourceType = ItemSourceType.fromText(type);
        return sourceType == null ? null : new ItemSource(sourceType, identifier);
    }

    public static ItemSource parseShorthand(String shorthand) {
        return ItemSourceRegistry.system().parseShorthand(shorthand);
    }

    public static void registerParser(ItemSourceParser parser) {
        ItemSourceRegistry.system().registerParser(parser);
    }

    public static String toShorthand(ItemSource source) {
        if (source == null || source.getType() == null || Texts.isBlank(source.getIdentifier())) {
            return null;
        }
        return switch (source.getType()) {
            case MMOITEMS ->
                "mmoitems-" + source.getIdentifier();
            case NEIGEITEMS ->
                "neigeitems-" + source.getIdentifier();
            case CRAFTENGINE ->
                "craftengine-" + source.getIdentifier();
            case VANILLA ->
                source.getIdentifier();
        };
    }

    public static boolean matches(ItemSource left, ItemSource right) {
        if (left == null || right == null || left.getType() != right.getType()) {
            return false;
        }
        return Texts.lower(left.getIdentifier()).equals(Texts.lower(right.getIdentifier()));
    }
}
