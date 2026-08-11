package emaki.jiuwu.craft.forge.api.model;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of one material slot in a forging recipe.
 *
 * <p>Stat and attribute contributions are exposed because third parties commonly want to explain to
 * players what a material adds. Name and lore modification tables are configuration internals and are
 * not exposed.
 *
 * @param id                     canonical lowercase material id
 * @param itemId                 item source shorthand such as {@code minecraft-iron_ingot}
 * @param amount                 how many items this slot consumes
 * @param capacityCost           capacity units this material occupies
 * @param optional               whether the material may be omitted
 * @param statContributions      stat id to added value
 * @param attributeContributions attribute id to added value
 * @param skillIds               skill ids granted by this material
 */
public record ForgeMaterialView(@NotNull String id,
                                @NotNull String itemId,
                                int amount,
                                int capacityCost,
                                boolean optional,
                                @NotNull Map<String, Double> statContributions,
                                @NotNull Map<String, Double> attributeContributions,
                                @NotNull List<String> skillIds) {

    /**
     * Normalises every reference component so no accessor can return {@code null}.
     *
     * @param id                     canonical lowercase material id
     * @param itemId                 item source shorthand
     * @param amount                 consumed amount
     * @param capacityCost           capacity units occupied
     * @param optional               whether omittable
     * @param statContributions      stat contributions
     * @param attributeContributions attribute contributions
     * @param skillIds               granted skill ids
     */
    public ForgeMaterialView {
        id = id == null ? "" : id;
        itemId = itemId == null ? "" : itemId;
        amount = Math.max(0, amount);
        capacityCost = Math.max(0, capacityCost);
        statContributions = statContributions == null ? Map.of() : Map.copyOf(statContributions);
        attributeContributions = attributeContributions == null ? Map.of() : Map.copyOf(attributeContributions);
        skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
    }
}
