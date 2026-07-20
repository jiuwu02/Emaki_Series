package emaki.jiuwu.craft.corelib.assembly;

import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;














final class LedgerNameComposer {

    private LedgerNameComposer() {
    }






    static Component resolveBaseName(ItemStack itemStack, ItemMeta itemMeta) {
        if (ItemTextBridge.hasCustomName(itemMeta)) {
            Component customName = ItemTextBridge.customName(itemMeta);
            if (customName != null) {
                return customName;
            }
        }
        return ItemTextBridge.effectiveName(itemStack);
    }







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





    static void writeName(ItemStack itemStack, ItemMeta itemMeta, Component name) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        ItemMeta targetMeta = itemMeta != null ? itemMeta : itemStack.getItemMeta();
        if (targetMeta == null) {
            return;
        }
        ItemTextBridge.customName(targetMeta, normalize(name));
        itemStack.setItemMeta(targetMeta);
    }

    private static Component normalize(Component component) {
        return component == null ? Component.empty() : component;
    }
}
