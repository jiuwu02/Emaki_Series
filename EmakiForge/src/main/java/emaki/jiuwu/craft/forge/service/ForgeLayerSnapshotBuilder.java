package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.EmakiPresentationEntry;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.assembly.ItemPresentationCompiler;
import emaki.jiuwu.craft.corelib.assembly.PresentationCompileIssue;
import emaki.jiuwu.craft.corelib.assembly.PresentationCompileResult;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeLayerSnapshotBuilder {

    private final EmakiForgePlugin plugin;
    private final ItemPresentationCompiler presentationCompiler;

    ForgeLayerSnapshotBuilder(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        this.presentationCompiler = coreLib == null ? new ItemPresentationCompiler() : coreLib.itemPresentationCompiler();
    }

    EmakiItemLayerSnapshot buildLayerSnapshot(Recipe recipe,
            GuiItems guiItems,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            long forgedAt) {
        return buildLayerSnapshot(recipe, collectMaterialContributions(recipe, guiItems), multiplier, qualityTier, forgedAt);
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

    List<ForgeMaterialContribution> collectMaterialContributions(Recipe recipe, GuiItems guiItems) {
        List<ForgeMaterialContribution> materials = new ArrayList<>();
        if (recipe == null || guiItems == null) {
            return materials;
        }
        int sequence = 0;
        for (Map.Entry<Integer, ItemStack> entry : guiItems.requiredMaterials().entrySet()) {
            ForgeMaterialContribution contribution = toMaterialContribution(recipe, entry.getKey(), entry.getValue(), "required", sequence++, false);
            if (contribution != null) {
                materials.add(contribution);
            }
        }
        for (Map.Entry<Integer, ItemStack> entry : guiItems.optionalMaterials().entrySet()) {
            ForgeMaterialContribution contribution = toMaterialContribution(recipe, entry.getKey(), entry.getValue(), "optional", sequence++, true);
            if (contribution != null) {
                materials.add(contribution);
            }
        }
        materials.sort((left, right) -> Integer.compare(left.sequence(), right.sequence()));
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
                        material.material().key() + "#" + material.sequence(),
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
            sequence = appendCompiledEntries(
                    entries,
                    presentationCompiler.compile(
                            recipe.result().nameModifications(),
                            recipe.result().loreActions(),
                            sequence,
                            "recipe_result"
                    )
            );
        }
        for (ForgeMaterialContribution material : materials) {
            sequence = appendCompiledEntries(
                    entries,
                    presentationCompiler.compile(
                            material.material().nameModifications(),
                            material.material().loreActions(),
                            sequence,
                            material.material().key()
                    )
            );
        }
        if (qualityTier != null) {
            QualitySettings settings = plugin.appConfig().qualitySettings();
            if (settings.itemMetaEnabled()) {
                sequence = appendCompiledEntries(
                        entries,
                        presentationCompiler.compile(
                                settings.itemMetaNameModifications(qualityTier.name()),
                                settings.itemMetaLoreActions(qualityTier.name()),
                                sequence,
                                "quality:" + qualityTier.name()
                        )
                );
            }
        }
        return entries;
    }

    private int appendCompiledEntries(List<EmakiPresentationEntry> entries, PresentationCompileResult compileResult) {
        if (compileResult == null) {
            return entries.isEmpty() ? 0 : entries.get(entries.size() - 1).sequenceOrder() + 1;
        }
        entries.addAll(compileResult.entries());
        logCompileIssues(compileResult.issues());
        return compileResult.nextSequence();
    }

    private void logCompileIssues(List<PresentationCompileIssue> issues) {
        if (plugin.messageService() == null || issues == null || issues.isEmpty()) {
            return;
        }
        for (PresentationCompileIssue issue : issues) {
            if (issue == null) {
                continue;
            }
            String messageKey = switch (issue.reason()) {
                case INVALID_ACTION_NAME ->
                    "console.lore_invalid_action_name";
                case INVALID_REGEX ->
                    "console.lore_invalid_regex";
                default ->
                    "console.lore_invalid_search_insert_config";
            };
            Map<String, Object> replacements = new LinkedHashMap<>();
            replacements.put("source", issue.sourceId().isBlank() ? "unknown" : issue.sourceId());
            replacements.put("action", issue.action());
            replacements.put("pattern", issue.targetPattern());
            replacements.put("error", issue.detail());
            plugin.messageService().warning(messageKey, replacements);
        }
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
        if (recipe != null && recipe.configuredOutputSource() != null) {
            audit.put("output_item", ItemSourceUtil.toShorthand(recipe.configuredOutputSource()));
        }
        List<Map<String, Object>> materialMaps = new ArrayList<>();
        for (ForgeMaterialContribution material : materials) {
            materialMaps.add(material.toAuditMap());
        }
        audit.put("materials", materialMaps);
        return audit;
    }

    private ForgeMaterialContribution toMaterialContribution(Recipe recipe,
            int slot,
            ItemStack itemStack,
            String category,
            int sequence,
            boolean optional) {
        if (itemStack == null || recipe == null) {
            return null;
        }
        ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
        ForgeMaterial material = recipe.findMaterialBySource(source, optional);
        if (material == null) {
            return null;
        }
        return new ForgeMaterialContribution(material, itemStack.getAmount(), slot, category, sequence, source);
    }
}
