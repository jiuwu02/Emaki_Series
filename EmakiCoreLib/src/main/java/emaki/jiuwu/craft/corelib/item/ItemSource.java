package emaki.jiuwu.craft.corelib.item;

import java.util.Objects;

public final class ItemSource {

    private final ItemSourceType type;
    private final String identifier;

    public ItemSource(ItemSourceType type, String identifier) {
        this.type = type;
        this.identifier = identifier;
    }

    public ItemSourceType getType() {
        return type;
    }

    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String toString() {
        return "ItemSource{" +
            "type=" + type +
            ", identifier='" + identifier + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemSource that)) {
            return false;
        }
        return type == that.type && Objects.equals(identifier, that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, identifier);
    }
}
