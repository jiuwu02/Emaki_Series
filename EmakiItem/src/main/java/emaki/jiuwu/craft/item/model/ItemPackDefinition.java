package emaki.jiuwu.craft.item.model;

import java.util.List;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record ItemPackDefinition(String packId,
        String displayName,
        String icon,
        List<String> lore,
        int order) {

    public static final int DEFAULT_ORDER = 100;
    public static final String DEFAULT_ICON = "minecraft-chest";

    public ItemPackDefinition {
        packId = packId == null ? "" : packId;
        displayName = Texts.isBlank(displayName) ? "" : displayName;
        icon = Texts.isBlank(icon) ? DEFAULT_ICON : icon;
        lore = lore == null || lore.isEmpty() ? List.of() : List.copyOf(lore);
    }

    public static ItemPackDefinition fallback(String packId) {
        return new ItemPackDefinition(packId, "", DEFAULT_ICON, List.of(), DEFAULT_ORDER);
    }

    public boolean hasDisplayName() {
        return Texts.isNotBlank(displayName);
    }

    public boolean hasLore() {
        return !lore.isEmpty();
    }
}
