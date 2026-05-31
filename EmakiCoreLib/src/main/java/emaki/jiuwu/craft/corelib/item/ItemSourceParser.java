package emaki.jiuwu.craft.corelib.item;

@FunctionalInterface
public interface ItemSourceParser {

    ItemSource parse(String shorthand);
}
