package emaki.jiuwu.craft.forge.service;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStructuredPresentation;
import emaki.jiuwu.craft.corelib.assembly.LoreOperationRegistry;
import emaki.jiuwu.craft.corelib.assembly.NameOperationRegistry;
import emaki.jiuwu.craft.corelib.assembly.OperationTemplateRenderer;
import emaki.jiuwu.craft.corelib.assembly.StructuredPresentationValidator;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeLayerSnapshotBuilder {

    private static final String NAMESPACE_ID = "forge";

    private final EmakiForgePlugin plugin;
    private final ForgeMaterialContributionCollector contributionCollector;
    private final ForgeStatContributionBuilder statBuilder;
    private final ForgePresentationBuilder presentationBuilder;
    private final ForgeAuditBuilder auditBuilder;

    ForgeLayerSnapshotBuilder(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        this.contributionCollector = new ForgeMaterialContributionCollector(plugin);
        OperationTemplateRenderer templateRenderer = new OperationTemplateRenderer();
        NameOperationRegistry nameOperations = new NameOperationRegistry(templateRenderer);
        LoreOperationRegistry loreOperations = new LoreOperationRegistry(templateRenderer);
        this.statBuilder = new ForgeStatContributionBuilder();
        this.presentationBuilder = new ForgePresentationBuilder(
                templateRenderer,
                nameOperations,
                loreOperations,
                new StructuredPresentationValidator()
        );
        this.auditBuilder = new ForgeAuditBuilder();
    }

    EmakiItemLayerSnapshot buildLayerSnapshot(Recipe recipe,
            GuiItems guiItems,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            long forgedAt,
            Player player) {
        return buildLayerSnapshot(recipe, collectMaterialContributions(recipe, guiItems), multiplier, qualityTier, forgedAt, player);
    }

    EmakiItemLayerSnapshot buildLayerSnapshot(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            long forgedAt,
            Player player) {
        List<ForgeMaterialContribution> contributions = materials == null ? List.of() : List.copyOf(materials);
        List<EmakiStatContribution> stats = statBuilder.buildStatContributions(contributions, multiplier);
        EmakiStructuredPresentation structuredPresentation = presentationBuilder.buildPresentation(
                recipe,
                contributions,
                qualityTier,
                multiplier,
                stats,
                qualitySettings(),
                player
        );
        Map<String, Object> audit = auditBuilder.buildAudit(recipe, contributions, qualityTier, multiplier, forgedAt);
        return new EmakiItemLayerSnapshot(
                NAMESPACE_ID,
                1,
                audit,
                stats,
                structuredPresentation == null || structuredPresentation.isEmpty() ? null : structuredPresentation
        );
    }

    List<ForgeMaterialContribution> collectMaterialContributions(Recipe recipe, GuiItems guiItems) {
        return contributionCollector.collectMaterialContributions(recipe, guiItems);
    }

    List<ForgeMaterial.QualityModifier> collectQualityModifiers(List<ForgeMaterialContribution> materials) {
        return contributionCollector.collectQualityModifiers(materials);
    }

    String buildMaterialsSignature(List<ForgeMaterialContribution> materials) {
        return auditBuilder.buildMaterialsSignature(materials);
    }

    private QualitySettings qualitySettings() {
        if (plugin == null || plugin.appConfig() == null || plugin.appConfig().qualitySettings() == null) {
            return QualitySettings.defaults();
        }
        return plugin.appConfig().qualitySettings();
    }
}
