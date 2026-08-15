package emaki.jiuwu.craft.mobs.loot;

public record LootFunctionDefinition(
        String type,
        CountRange count
) {
    public record CountRange(int min, int max) {
    }
}
