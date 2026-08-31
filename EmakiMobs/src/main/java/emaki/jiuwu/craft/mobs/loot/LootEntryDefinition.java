package emaki.jiuwu.craft.mobs.loot;

import java.util.List;

public record LootEntryDefinition(
        String item,
        String emakiItem,
        int weight,
        double chance,
        List<LootFunctionDefinition> functions
) {
}
