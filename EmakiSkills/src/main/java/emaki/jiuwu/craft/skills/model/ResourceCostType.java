package emaki.jiuwu.craft.skills.model;

import java.util.Locale;

public enum ResourceCostType {

    EA_RESOURCE,
    ATTRIBUTE_CHECK,
    LOCAL_RESOURCE,
    AURASKILLS_MANA,
    MYTHICLIB_MANA;

    public static ResourceCostType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "ea-resource" -> EA_RESOURCE;
            case "attribute-check", "ea-attribute-check" -> ATTRIBUTE_CHECK;
            case "local-resource" -> LOCAL_RESOURCE;
            case "auraskills-mana", "aura-skills-mana", "aura-mana" -> AURASKILLS_MANA;
            case "mmocore-mana", "mythiclib-mana", "mythic-mana" -> MYTHICLIB_MANA;
            default -> null;
        };
    }
}
