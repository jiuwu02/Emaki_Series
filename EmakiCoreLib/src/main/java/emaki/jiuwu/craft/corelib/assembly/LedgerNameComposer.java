package emaki.jiuwu.craft.corelib.assembly;

import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import net.kyori.adventure.text.Component;
import emaki.jiuwu.craft.corelib.api.assembly.BaseNamePolicy;
import emaki.jiuwu.craft.corelib.api.assembly.ItemOperationEntry;

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
        LocalNameState state = new LocalNameState();
        for (ItemOperationEntry.NameOperationRecord record : records) {
            if (record == null) {
                continue;
            }
            String action = Texts.lower(record.action());
            String value = record.renderedValue();
            switch (action) {
                case "replace" -> state.replaceBase(value);
                case "prepend_prefix" -> state.addPrefix(value);
                case "append_suffix" -> state.addPostfix(value);
                case "regex_replace" -> state.applyRegexReplace(
                        record.regexPattern(),
                        value,
                        Map.of()
                );
                default -> {
                }
            }
        }
        return composeFromState(state, current);
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
