package emaki.jiuwu.craft.corelib.assembly;

import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

/**
 * Composes ledger item names as adventure {@link Component}s and writes them
 * back while preserving translatable base names.
 *
 * <p>The operation ledger historically round-tripped names through legacy
 * strings ({@code getDisplayName}/{@code setDisplayName}). Legacy serialization
 * cannot represent a translation key, so a vanilla item name such as
 * {@code item.minecraft.diamond_sword} was flattened to the server locale
 * (e.g. English) whenever a name operation ran. This helper keeps the base name
 * as a component and only treats the configured prefix/postfix segments as
 * MiniMessage text, so vanilla items keep their localized name while suffixes
 * like {@code [+1]} are still appended.
 */
final class LedgerNameComposer {

    private LedgerNameComposer() {
    }

    /**
     * Resolves the base name component for an item that has no ledger-applied
     * name overlay yet: an existing custom name when present, otherwise the
     * item's effective (translatable) name.
     */
    static Component resolveBaseName(ItemStack itemStack, ItemMeta itemMeta) {
        if (ItemTextBridge.hasCustomName(itemMeta)) {
            Component customName = ItemTextBridge.customName(itemMeta);
            if (customName != null) {
                return customName;
            }
        }
        return ItemTextBridge.effectiveName(itemStack);
    }

    /**
     * Builds the final name from a {@link LocalNameState} reduced from a single
     * batch of name operations. When the state uses an explicit template that
     * template becomes the base; otherwise {@code baseName} is preserved as-is
     * so a translation key survives.
     */
    static Component composeFromState(LocalNameState state, Component baseName) {
        if (state == null) {
            return baseName;
        }
        Component base;
        if (state.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE && Texts.isNotBlank(state.baseNameTemplate())) {
            base = MiniMessages.parse(state.baseNameTemplate());
        } else {
            base = normalize(baseName);
        }
        Component result = Component.empty();
        for (String prefix : state.prefixes()) {
            if (Texts.isNotBlank(prefix)) {
                result = result.append(MiniMessages.parse(prefix));
            }
        }
        result = result.append(base);
        for (String postfix : state.postfixes()) {
            if (Texts.isNotBlank(postfix)) {
                result = result.append(MiniMessages.parse(postfix));
            }
        }
        return result;
    }

    /**
     * Builds the final name by replaying an ordered list of name records on top
     * of {@code baseName}. A {@code replace} record swaps the base for its
     * rendered value; {@code prepend_prefix}/{@code append_suffix} wrap the
     * current component. Unknown actions are ignored.
     */
    static Component composeFromRecords(Component baseName, List<ItemOperationEntry.NameOperationRecord> records) {
        Component current = normalize(baseName);
        if (records == null || records.isEmpty()) {
            return current;
        }
        for (ItemOperationEntry.NameOperationRecord record : records) {
            if (record == null) {
                continue;
            }
            current = applyRecord(current, record);
        }
        return current;
    }

    private static Component applyRecord(Component current, ItemOperationEntry.NameOperationRecord record) {
        String action = Texts.lower(record.action());
        String value = record.renderedValue();
        return switch (action) {
            case "replace" -> Texts.isBlank(value) ? current : MiniMessages.parse(value);
            case "prepend_prefix" -> Texts.isBlank(value)
                    ? current
                    : Component.empty().append(MiniMessages.parse(value)).append(current);
            case "append_suffix" -> Texts.isBlank(value)
                    ? current
                    : Component.empty().append(current).append(MiniMessages.parse(value));
            default -> current;
        };
    }

    /**
     * Writes the composed name back to the item, preferring component injection
     * (which preserves translation keys) and falling back to the legacy custom
     * name path when component injection is unavailable.
     */
    static void writeName(ItemStack itemStack, ItemMeta itemMeta, Component name) {
        Component effective = normalize(name);
        if (SpigotItemComponentNameWriter.writeCustomName(itemStack, effective)) {
            return;
        }
        ItemMeta targetMeta = itemMeta != null ? itemMeta : itemStack.getItemMeta();
        if (targetMeta == null) {
            return;
        }
        ItemTextBridge.customName(targetMeta, effective);
        itemStack.setItemMeta(targetMeta);
    }

    private static Component normalize(Component component) {
        return component == null ? Component.empty() : component;
    }
}
