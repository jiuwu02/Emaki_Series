package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.EmakiPresentationEntry;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeLayerSnapshotBuilder {

    private final EmakiForgePlugin plugin;

    ForgeLayerSnapshotBuilder(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    EmakiItemLayerSnapshot buildLayerSnapshot(Recipe recipe,
            GuiItems guiItems,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            long forgedAt) {
        return buildLayerSnapshot(recipe, collectMaterialContributions(guiItems), multiplier, qualityTier, forgedAt);
    }

    EmakiItemLayerSnapshot buildLayerSnapshot(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            long forgedAt) {
        List<ForgeMaterialContribution> contributions = materials == null ? List.of() : List.copyOf(materials);
        List<EmakiStatContribution> stats = buildStatContributions(contributions, multiplier);
        List<EmakiPresentationEntry> presentation = buildPresentationEntries(recipe, contributions, qualityTier);
        Map<String, Object> audit = buildAudit(recipe, contributions, qualityTier, multiplier, forgedAt);
        return new EmakiItemLayerSnapshot("forge", 1, audit, stats, presentation);
    }

    List<ForgeMaterialContribution> collectMaterialContributions(GuiItems guiItems) {
        List<ForgeMaterialContribution> materials = new ArrayList<>();
        if (guiItems == null) {
            return materials;
        }
        int sequence = 0;
        for (Map.Entry<Integer, ItemStack> entry : guiItems.requiredMaterials().entrySet()) {
            ForgeMaterialContribution contribution = toMaterialContribution(entry.getKey(), entry.getValue(), "required", sequence++);
            if (contribution != null) {
                materials.add(contribution);
            }
        }
        for (Map.Entry<Integer, ItemStack> entry : guiItems.optionalMaterials().entrySet()) {
            ForgeMaterialContribution contribution = toMaterialContribution(entry.getKey(), entry.getValue(), "optional", sequence++);
            if (contribution != null) {
                materials.add(contribution);
            }
        }
        materials.sort(Comparator.comparingInt((ForgeMaterialContribution value) -> value.material().priority())
                .thenComparingInt(ForgeMaterialContribution::sequence));
        return materials;
    }

    List<ForgeMaterial.QualityModifier> collectQualityModifiers(List<ForgeMaterialContribution> materials) {
        List<ForgeMaterial.QualityModifier> result = new ArrayList<>();
        if (materials == null) {
            return result;
        }
        for (ForgeMaterialContribution material : materials) {
            if (material == null || material.amount() <= 0) {
                continue;
            }
            result.addAll(material.qualityModifiers());
        }
        return result;
    }

    String buildMaterialsSignature(List<ForgeMaterialContribution> materials) {
        List<Map<String, Object>> signatureData = new ArrayList<>();
        if (materials != null) {
            for (ForgeMaterialContribution material : materials) {
                if (material != null && material.amount() > 0) {
                    signatureData.add(material.toSignatureData());
                }
            }
        }
        return SignatureUtil.stableSignature(signatureData);
    }

    private List<EmakiStatContribution> buildStatContributions(List<ForgeMaterialContribution> materials, double multiplier) {
        List<EmakiStatContribution> stats = new ArrayList<>();
        int sequence = 0;
        for (ForgeMaterialContribution material : materials) {
            for (Map.Entry<String, Double> entry : material.material().statContributions().entrySet()) {
                stats.add(new EmakiStatContribution(
                        entry.getKey(),
                        entry.getValue() * material.amount() * multiplier,
                        material.material().id() + "#" + material.sequence(),
                        sequence++
                ));
            }
        }
        return stats;
    }

    private List<EmakiPresentationEntry> buildPresentationEntries(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            QualitySettings.QualityTier qualityTier) {
        List<EmakiPresentationEntry> entries = new ArrayList<>();
        int sequence = 0;
        if (recipe != null && recipe.result() != null) {
            sequence = addNameEntries(entries, recipe.result().nameModifications(), sequence, "recipe_result");
            sequence = addLoreEntries(entries, recipe.result().loreActions(), sequence, "recipe_result");
        }
        for (ForgeMaterialContribution material : materials) {
            sequence = addNameEntries(entries, material.material().nameModifications(), sequence, material.material().id());
            sequence = addLoreEntries(entries, material.material().loreActions(), sequence, material.material().id());
        }
        if (qualityTier != null) {
            QualitySettings settings = plugin.appConfig().qualitySettings();
            if (settings.itemMetaEnabled()) {
                sequence = addNameEntries(entries, settings.itemMetaNameModifications(qualityTier.name()), sequence, "quality:" + qualityTier.name());
                sequence = addLoreEntries(entries, settings.itemMetaLoreActions(qualityTier.name()), sequence, "quality:" + qualityTier.name());
            }
        }
        return entries;
    }

    private int addNameEntries(List<EmakiPresentationEntry> entries,
            List<Map<String, Object>> modifications,
            int sequence,
            String sourceId) {
        if (modifications == null) {
            return sequence;
        }
        for (Map<String, Object> modification : modifications) {
            String action = Texts.lower(modification.get("action"));
            String value = Texts.toStringSafe(modification.get("value"));
            switch (action) {
                case "append_suffix" ->
                    entries.add(new EmakiPresentationEntry("name_append", "", value, sequence++, sourceId));
                case "prepend_prefix" ->
                    entries.add(new EmakiPresentationEntry("name_prepend", "", value, sequence++, sourceId));
                case "replace" ->
                    entries.add(new EmakiPresentationEntry("name_replace", "", value, sequence++, sourceId));
                case "regex_replace" ->
                    entries.add(new EmakiPresentationEntry(
                            "name_regex_replace",
                            Texts.toStringSafe(modification.get("regex_pattern")),
                            Texts.toStringSafe(modification.get("replacement")),
                            sequence++,
                            sourceId
                    ));
                default -> {
                }
            }
        }
        return sequence;
    }

    private int addLoreEntries(List<EmakiPresentationEntry> entries,
            List<Map<String, Object>> loreActions,
            int sequence,
            String sourceId) {
        if (loreActions == null) {
            return sequence;
        }
        for (Map<String, Object> action : loreActions) {
            String type = Texts.lower(action.get("action"));
            List<String> content = Texts.asStringList(action.get("content"));
            String targetPattern = Texts.toStringSafe(action.get("target_pattern"));
            String regexPattern = Texts.toStringSafe(action.get("regex_pattern"));
            String replacement = Texts.toStringSafe(action.get("replacement"));
            switch (type) {
                case "insert_below", "insert_above", "append", "append_line", "append_lines", "prepend_line", "prepend_lines", "append_first_line", "append_first_lines", "insert_first" -> {
                    String entryType = mapLoreEntryType(type);
                    String anchor = "insert_below".equals(type) || "insert_above".equals(type) ? targetPattern : "";
                    for (String line : content) {
                        String statId = firstPlaceholder(line);
                        if (Texts.isNotBlank(statId)) {
                            entries.add(new EmakiPresentationEntry("stat_line", anchor, line, sequence++, statId));
                        } else {
                            entries.add(new EmakiPresentationEntry(entryType, anchor, line, sequence++, sourceId));
                        }
                    }
                }
                case "replace_line" -> {
                    String line = content.isEmpty() ? "" : content.get(0);
                    String statId = firstPlaceholder(line);
                    if (Texts.isNotBlank(statId)) {
                        entries.add(new EmakiPresentationEntry("stat_line", targetPattern, line, sequence++, statId));
                    } else {
                        entries.add(new EmakiPresentationEntry("lore_replace_line", targetPattern, line, sequence++, sourceId));
                    }
                }
                case "delete_line" ->
                    entries.add(new EmakiPresentationEntry("lore_delete_line", targetPattern, "", sequence++, sourceId));
                case "regex_replace" ->
                    entries.add(new EmakiPresentationEntry("lore_regex_replace", regexPattern, replacement, sequence++, sourceId));
                default -> {
                }
            }
        }
        return sequence;
    }

    private String mapLoreEntryType(String type) {
        return switch (Texts.lower(type)) {
            case "insert_below" ->
                "lore_insert_below";
            case "insert_above" ->
                "lore_insert_above";
            case "prepend_line", "prepend_lines", "append_first_line", "append_first_lines", "insert_first" ->
                "lore_prepend";
            default ->
                "lore_append";
        };
    }

    private String firstPlaceholder(String template) {
        if (Texts.isBlank(template)) {
            return "";
        }
        int open = template.indexOf('{');
        int close = template.indexOf('}', open + 1);
        if (open < 0 || close <= open + 1) {
            return "";
        }
        return Texts.lower(template.substring(open + 1, close));
    }

    private Map<String, Object> buildAudit(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            QualitySettings.QualityTier qualityTier,
            double multiplier,
            long forgedAt) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("recipe_id", recipe == null ? "" : recipe.id());
        audit.put("quality", qualityTier == null ? "" : qualityTier.name());
        audit.put("multiplier", multiplier);
        audit.put("forged_at", forgedAt);
        audit.put("materials_signature", buildMaterialsSignature(materials));
        if (recipe != null && recipe.result() != null && recipe.result().outputItem() != null) {
            audit.put("output_item", ItemSourceUtil.toShorthand(recipe.result().outputItem()));
        }
        List<Map<String, Object>> materialMaps = new ArrayList<>();
        for (ForgeMaterialContribution material : materials) {
            materialMaps.add(material.toAuditMap());
        }
        audit.put("materials", materialMaps);
        return audit;
    }

    private ForgeMaterialContribution toMaterialContribution(int slot, ItemStack itemStack, String category, int sequence) {
        if (itemStack == null) {
            return null;
        }
        ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
        ForgeMaterial material = findMaterialBySource(source);
        if (material == null) {
            return null;
        }
        return new ForgeMaterialContribution(material, itemStack.getAmount(), slot, category, sequence, source);
    }

    private ForgeMaterial findMaterialBySource(ItemSource source) {
        if (source == null) {
            return null;
        }
        for (ForgeMaterial material : plugin.materialLoader().all().values()) {
            if (ItemSourceUtil.matches(source, material.source())) {
                return material;
            }
        }
        return null;
    }
}
