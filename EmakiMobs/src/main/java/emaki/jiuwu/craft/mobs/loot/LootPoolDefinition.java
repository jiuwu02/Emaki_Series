package emaki.jiuwu.craft.mobs.loot;

import java.util.List;

public record LootPoolDefinition(
        Object rolls,
        List<LootEntryDefinition> entries
) {
}
