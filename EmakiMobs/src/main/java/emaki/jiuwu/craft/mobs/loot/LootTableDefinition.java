package emaki.jiuwu.craft.mobs.loot;

import java.util.List;

public record LootTableDefinition(
        String mobId,
        List<LootPoolDefinition> pools
) {
}
