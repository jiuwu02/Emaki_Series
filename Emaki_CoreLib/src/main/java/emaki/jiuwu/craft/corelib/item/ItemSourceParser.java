package emaki.jiuwu.craft.corelib.item;

/**
 * Parses a shorthand item-source string into a normalized {@link ItemSource}.
 */
@FunctionalInterface
public interface ItemSourceParser {

    /**
     * Attempts to parse the provided shorthand.
     *
     * @param shorthand raw shorthand such as {@code ni-item_id} or {@code ce-namespace:id}
     * @return a parsed {@link ItemSource}, or {@code null} when this parser does not handle the input
     */
    ItemSource parse(String shorthand);
}
