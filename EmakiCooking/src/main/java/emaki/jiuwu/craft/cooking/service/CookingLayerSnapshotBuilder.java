package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.EmakiStructuredPresentation;
import emaki.jiuwu.craft.corelib.assembly.StructuredPresentationTemplateResolver;
import emaki.jiuwu.craft.corelib.assembly.StructuredPresentationValidator;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;

public final class CookingLayerSnapshotBuilder {

    private static final String NAMESPACE_ID = "cooking";

    private final StructuredPresentationTemplateResolver structuredResolver = new StructuredPresentationTemplateResolver();
    private final StructuredPresentationValidator structuredValidator = new StructuredPresentationValidator();

    public EmakiItemLayerSnapshot buildSnapshot(RecipeDocument recipe,
            Map<String, Object> output,
            String phase,
            Map<String, ?> placeholders) {
        Map<String, Object> normalizedOutput = output == null || output.isEmpty()
                ? Map.of()
                : Map.copyOf(MapYamlSection.normalizeMap(output));
        Map<String, Object> replacements = buildReplacements(recipe, normalizedOutput, phase, placeholders);
        EmakiStructuredPresentation structuredPresentation = buildStructuredPresentation(recipe, normalizedOutput, replacements);
        return new EmakiItemLayerSnapshot(
                NAMESPACE_ID,
                1,
                buildAudit(recipe, normalizedOutput, phase, placeholders),
                List.of(),
                structuredPresentation
        );
    }

    private EmakiStructuredPresentation buildStructuredPresentation(RecipeDocument recipe,
            Map<String, Object> output,
            Map<String, Object> replacements) {
        List<EmakiStructuredPresentation> presentations = new ArrayList<>();
        if (recipe != null) {
            presentations.add(structuredResolver.fromConfig(
                    recipe.configuration().get("structured_presentation"),
                    replacements,
                    NAMESPACE_ID
            ));
        }
        presentations.add(structuredResolver.fromConfig(
                output == null ? null : output.get("structured_presentation"),
                replacements,
                NAMESPACE_ID
        ));
        StructuredPresentationValidator.ValidationResult validation = structuredValidator.sanitize(
                structuredResolver.merge(presentations)
        );
        return validation.presentation();
    }

    private Map<String, Object> buildAudit(RecipeDocument recipe,
            Map<String, Object> output,
            String phase,
            Map<String, ?> placeholders) {
        Map<String, Object> audit = new LinkedHashMap<>();
        if (recipe != null) {
            audit.put("recipe_id", recipe.id());
            audit.put("recipe_name", recipe.displayName());
            audit.put("station_type", recipe.stationType().folderName());
        }
        putIfPresent(audit, "phase", Texts.trim(phase));
        putIfPresent(audit, "source", output == null ? null : output.get("source"));
        putIfPresent(audit, "amount", output == null ? null : output.get("amount"));
        putIfPresent(audit, "chance", output == null ? null : output.get("chance"));
        if (output != null && output.get("amount_range") instanceof Map<?, ?> amountRange) {
            audit.put("amount_range", MapYamlSection.normalizeMap(amountRange));
        }
        if (placeholders != null) {
            putIfPresent(audit, "context_recipe_id", placeholders.get("recipe_id"));
            putIfPresent(audit, "context_station_type", placeholders.get("station_type"));
            putIfPresent(audit, "context_outcome", placeholders.get("outcome"));
            putIfPresent(audit, "context_slot_index", placeholders.get("slot_index"));
        }
        return audit;
    }

    private Map<String, Object> buildReplacements(RecipeDocument recipe,
            Map<String, Object> output,
            String phase,
            Map<String, ?> placeholders) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        if (placeholders != null) {
            for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    replacements.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (recipe != null) {
            replacements.put("recipe_id", recipe.id());
            replacements.put("recipe_name", recipe.displayName());
            replacements.put("station_type", recipe.stationType().folderName());
        }
        putIfPresent(replacements, "phase", Texts.trim(phase));
        putIfPresent(replacements, "output_source", output == null ? null : output.get("source"));
        putIfPresent(replacements, "output_amount", output == null ? null : output.get("amount"));
        putIfPresent(replacements, "output_chance", output == null ? null : output.get("chance"));
        if (output != null && output.get("amount_range") instanceof Map<?, ?> amountRange) {
            Map<String, Object> normalizedRange = MapYamlSection.normalizeMap(amountRange);
            putIfPresent(replacements, "output_amount_min", normalizedRange.get("min"));
            putIfPresent(replacements, "output_amount_max", normalizedRange.get("max"));
        }
        return replacements;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (target == null || Texts.isBlank(key) || value == null) {
            return;
        }
        target.put(key, value);
    }
}
