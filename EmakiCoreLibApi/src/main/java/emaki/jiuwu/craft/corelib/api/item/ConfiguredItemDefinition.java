package emaki.jiuwu.craft.corelib.api.item;

import java.util.Map;
import java.util.Objects;

/**
 * Public, server-version-independent description of an item and its component patches.
 * Component IDs without a namespace are normalized to the {@code minecraft} namespace.
 */
public final class ConfiguredItemDefinition {

    private final String source;
    private final int amount;
    private final Map<String, ItemComponentPatch> components;

    public ConfiguredItemDefinition(String source, int amount, Map<String, ItemComponentPatch> components) {
        this.source = source == null || source.isBlank() ? null : source.trim();
        this.amount = Math.max(1, amount);
        this.components = PlainItemData.componentMap(components);
    }

    public ConfiguredItemDefinition(String source, Map<String, ItemComponentPatch> components) {
        this(source, 1, components);
    }

    public String source() {
        return source;
    }

    public int amount() {
        return amount;
    }

    public Map<String, ItemComponentPatch> components() {
        return components;
    }

    public ConfiguredItemDefinition withSource(String source) {
        return new ConfiguredItemDefinition(source, amount, components);
    }

    public ConfiguredItemDefinition withAmount(int amount) {
        return new ConfiguredItemDefinition(source, amount, components);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfiguredItemDefinition definition)) {
            return false;
        }
        return amount == definition.amount
                && Objects.equals(source, definition.source)
                && components.equals(definition.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, amount, components);
    }

    @Override
    public String toString() {
        return "ConfiguredItemDefinition[source=" + source + ", amount=" + amount + ", components=" + components + "]";
    }
}
