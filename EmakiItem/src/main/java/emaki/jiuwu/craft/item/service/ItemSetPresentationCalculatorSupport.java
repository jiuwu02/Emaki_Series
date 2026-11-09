package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.assembly.ItemOperationEntry;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.loader.EmakiItemSetLoader;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EquippedSetState;
import emaki.jiuwu.craft.item.model.ItemSetDefinition;
import emaki.jiuwu.craft.item.model.ItemSetMembership;
import emaki.jiuwu.craft.item.model.ItemSetThreshold;

final class ItemSetPresentationCalculatorSupport {

    static int uniqueBlockStart(List<String> lines, List<String> canonicalBlock) {
        int matchedStart = -1;
        if (lines == null || canonicalBlock == null || canonicalBlock.isEmpty()) {
            return matchedStart;
        }
        for (int start = 0; start <= lines.size() - canonicalBlock.size(); start++) {
            if (!matchesAt(lines, canonicalBlock, start)) {
                continue;
            }
            if (matchedStart >= 0) {
                return -1;
            }
            matchedStart = start;
        }
        return matchedStart;
}

    static boolean endsWith(List<String> lines, List<String> suffix) {
        return lines != null
                && suffix != null
                && !suffix.isEmpty()
                && lines.size() >= suffix.size()
                && matchesAt(lines, suffix, lines.size() - suffix.size());
}

    static boolean matchesAt(List<String> lines, List<String> canonicalBlock, int start) {
        if (lines == null || canonicalBlock == null || canonicalBlock.isEmpty()
                || start < 0 || start + canonicalBlock.size() > lines.size()) {
            return false;
        }
        for (int offset = 0; offset < canonicalBlock.size(); offset++) {
            if (!canonicalLoreLine(lines.get(start + offset)).equals(canonicalBlock.get(offset))) {
                return false;
            }
        }
        return true;
}

    static void removeSeparatorBefore(List<String> lines, int start) {
        int separatorIndex = start - 1;
        if (separatorIndex >= 0 && MiniMessages.plainText(lines.get(separatorIndex)).isBlank()) {
            lines.remove(separatorIndex);
        }
}

    static String canonicalLoreLine(String line) {
        return MiniMessages.serialize(MiniMessages.parse(Texts.toStringSafe(line)));
}
}
