package emaki.jiuwu.craft.dungeon.api;

public record InstanceView(String id,
        String dungeonId,
        String state,
        int members,
        String worldName) {
}
