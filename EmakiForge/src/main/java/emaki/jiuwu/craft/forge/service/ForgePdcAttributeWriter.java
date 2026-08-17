package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.ForgePdcKeys;
import emaki.jiuwu.craft.forge.integration.ForgeAttributeBridge;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPdcCodec;
import emaki.jiuwu.craft.skills.api.pdc.SkillPdcMutation;

final class ForgePdcAttributeWriter {

    private static final String SOURCE_ID = "forge";

    private final EmakiForgePlugin plugin;
    private final PdcService pdcService;

    ForgePdcAttributeWriter(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        this.pdcService = new PdcService(
                ForgePdcKeys.NAMESPACE,
                "pdc",
                plugin == null ? null : plugin.debugLogger()
        );
    }

    void apply(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            ItemStack itemStack) {
        if (itemStack == null) {
            return;
        }
        ForgeAttributeBridge gateway = plugin.pdcAttributeGateway();
        Map<String, Double> attributes = new LinkedHashMap<>();
        Map<String, String> meta = new LinkedHashMap<>();
        List<String> skillIds = new ArrayList<>();
        if (materials != null) {
            for (ForgeMaterialContribution contribution : materials) {
                if (contribution == null || contribution.material() == null || contribution.amount() <= 0) {
                    continue;
                }
                skillIds.addAll(contribution.material().skillIds());
                for (Map.Entry<String, Double> entry : contribution.material().attributeContributions().entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }
                    double value = entry.getValue() * contribution.amount() * multiplier;
                    if (Math.abs(value) <= 1.0E-9D) {
                        continue;
                    }
                    attributes.merge(entry.getKey(), value, Double::sum);
                }
            }
        }
        observeSkillMutation(itemStack, EquipmentSkillPdcCodec.write(itemStack, skillIds));
        // 独立锻造变量先落盘：即使本配方没有任何属性贡献（下方 attributes.isEmpty() 分支会提前
        // 返回），品质与配方 ID 也必须可读，否则 Strengthen 无法稳定读取容量来源。
        writeForgeVariables(recipe, multiplier, qualityTier, itemStack);
        if (gateway == null || !gateway.available()) {
            return;
        }
        if (attributes.isEmpty()) {
            gateway.clear(itemStack, SOURCE_ID);
            return;
        }
        if (recipe != null) {
            meta.put("recipe_id", recipe.id());
        }
        if (qualityTier != null) {
            meta.put("quality", qualityTier.name());
        }
        gateway.write(itemStack, SOURCE_ID, attributes, meta);
    }

    /**
     * 将品质与配方变量写入 Forge 自己的 PDC 分区。
     *
     * <p>与属性桥 payload 的 meta 并存而非替代：meta 的 {@code recipe_id} / {@code quality} 保持原样，
     * 老物品的读取方不受影响。
     */
    private void writeForgeVariables(Recipe recipe,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            ItemStack itemStack) {
        if (pdcService == null) {
            return;
        }
        PdcPartition partition = pdcService.partition(ForgePdcKeys.FORGE_PARTITION);
        if (recipe != null && Texts.isNotBlank(recipe.id())) {
            pdcService.set(itemStack, partition, ForgePdcKeys.FORGE_RECIPE_ID,
                    PersistentDataType.STRING, recipe.id());
        }
        if (qualityTier != null && Texts.isNotBlank(qualityTier.name())) {
            // QualityTier 只有 name/weight/multiplier，没有独立的 id 与 display 字段，
            // 因此标识与显示名同源取档位名。
            pdcService.set(itemStack, partition, ForgePdcKeys.QUALITY_ID,
                    PersistentDataType.STRING, qualityTier.name());
            pdcService.set(itemStack, partition, ForgePdcKeys.QUALITY_DISPLAY,
                    PersistentDataType.STRING, qualityTier.name());
        }
        if (Double.isFinite(multiplier)) {
            pdcService.set(itemStack, partition, ForgePdcKeys.QUALITY_MULTIPLIER,
                    PersistentDataType.STRING, Numbers.formatNumber(multiplier, "0.##"));
        }
    }

    private void observeSkillMutation(ItemStack itemStack, SkillPdcMutation mutation) {
        if (plugin == null
                || plugin.debugLogger() == null
                || mutation == null
                || !plugin.debugLogger().shouldLog("pdc", (UUID) null)) {
            return;
        }
        plugin.debugLogger().log("pdc", (UUID) null, "pdc.skill_payload", Map.of(
                "operation", mutation.operation(),
                "item", itemStack == null ? "null" : itemStack.getType(),
                "amount", itemStack == null ? 0 : itemStack.getAmount(),
                "before", mutation.before().values(),
                "after", mutation.after().values(),
                "committed", mutation.committed(),
                "reason", mutation.reason()
        ));
    }
}
