package emaki.jiuwu.craft.attribute.service;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.PdcAttributeAccess;
import emaki.jiuwu.craft.attribute.loader.PdcReadRuleLoader;
import emaki.jiuwu.craft.attribute.api.model.PdcAttributePayload;
import emaki.jiuwu.craft.attribute.model.PdcReadRule;
import emaki.jiuwu.craft.attribute.model.PdcReadRule.RuleCondition;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.pdc.SnapshotCodec;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import me.clip.placeholderapi.PlaceholderAPI;

final class PdcAttributeServiceDelegate {

    private final PdcAttributeService ownerRef;

    PdcAttributeServiceDelegate(PdcAttributeService ownerRef) {
        this.ownerRef = ownerRef;
    }

    boolean combineResults(List<Boolean> results, String conditionType, Integer requiredCount) {
        if (results == null || results.isEmpty()) {
            return true;
        }
        String mode = Texts.lower(conditionType);
        if ("any_of".equals(mode)) {
            return results.stream().anyMatch(Boolean::booleanValue);
        }
        if ("at_least".equals(mode)) {
            int count = requiredCount == null ? 1 : requiredCount;
            return results.stream().filter(Boolean::booleanValue).count() >= count;
        }
        if ("exactly".equals(mode)) {
            int count = requiredCount == null ? 1 : requiredCount;
            return results.stream().filter(Boolean::booleanValue).count() == count;
        }
        if ("none_of".equals(mode)) {
            return results.stream().noneMatch(Boolean::booleanValue);
        }
        return results.stream().allMatch(Boolean::booleanValue);
    }

    List<String> readLoreLines(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return List.of();
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null || !itemMeta.hasLore()) {
            return List.of();
        }
        var lore = ItemTextBridge.loreLines(itemMeta);
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (var line : lore) {
            String plain = Texts.normalizeWhitespace(MiniMessages.plainText(line));
            if (Texts.isNotBlank(plain)) {
                lines.add(plain);
            }
        }
        return lines.isEmpty() ? List.of() : List.copyOf(lines);
    }

}
