package emaki.jiuwu.craft.forge.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of a forging recipe.
 *
 * <p>Deliberately narrower than EmakiForge's internal {@code Recipe}: condition blocks, action
 * phases, failure outcome tables, and quality pools are configuration internals that would tie third
 * parties to EmakiForge's YAML shape.
 *
 * @param id                 canonical lowercase recipe id
 * @param displayName         display name; falls back to the id when unset
 * @param successRate         base success rate as a percentage in {@code [0, 100]}
 * @param requiredMaterials   materials that must be supplied
 * @param optionalMaterials   materials that may be supplied for bonuses
 * @param requiresTargetInput   whether the recipe consumes an existing item in the target slot
 * @param forgeCapacity         how many capacity units the recipe allows
 * @param optionalMaterialLimit how many optional materials may be supplied at once
 * @param permission            permission node required to use the recipe; empty when unrestricted
 * @param blueprints            blueprint items the recipe consumes; empty when none
 * @param hasFailureMechanism   whether the recipe can fail, meaning its success rate is below 100
 */
public record ForgeRecipeView(@NotNull String id,
                              @NotNull String displayName,
                              double successRate,
                              @NotNull List<ForgeMaterialView> requiredMaterials,
                              @NotNull List<ForgeMaterialView> optionalMaterials,
                              boolean requiresTargetInput,
                              int forgeCapacity,
                              int optionalMaterialLimit,
                              @NotNull String permission,
                              @NotNull List<ForgeBlueprintView> blueprints,
                              boolean hasFailureMechanism) {

    /**
     * Normalises every reference component so no accessor can return {@code null}.
     *
     * @param id                    canonical lowercase recipe id
     * @param displayName           display name
     * @param successRate           base success rate percentage
     * @param requiredMaterials     required materials
     * @param optionalMaterials     optional materials
     * @param requiresTargetInput   whether a target input is consumed
     * @param forgeCapacity         capacity units
     * @param optionalMaterialLimit optional material slot limit
     * @param permission            required permission node
     * @param blueprints            blueprint requirements
     * @param hasFailureMechanism   whether the recipe can fail
     */
    public ForgeRecipeView {
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        requiredMaterials = requiredMaterials == null ? List.of() : List.copyOf(requiredMaterials);
        optionalMaterials = optionalMaterials == null ? List.of() : List.copyOf(optionalMaterials);
        permission = permission == null ? "" : permission;
        blueprints = blueprints == null ? List.of() : List.copyOf(blueprints);
        optionalMaterialLimit = Math.max(0, optionalMaterialLimit);
    }

    /** {@return whether this recipe requires at least one blueprint item} */
    public boolean requiresBlueprint() {
        return !blueprints.isEmpty();
    }

    /** {@return whether a permission node gates this recipe} */
    public boolean requiresPermission() {
        return !permission.isEmpty();
    }

    /** {@return every material, required first then optional} */
    public @NotNull List<ForgeMaterialView> allMaterials() {
        if (optionalMaterials.isEmpty()) {
            return requiredMaterials;
        }
        if (requiredMaterials.isEmpty()) {
            return optionalMaterials;
        }
        List<ForgeMaterialView> combined = new java.util.ArrayList<>(requiredMaterials.size() + optionalMaterials.size());
        combined.addAll(requiredMaterials);
        combined.addAll(optionalMaterials);
        return List.copyOf(combined);
    }
}
