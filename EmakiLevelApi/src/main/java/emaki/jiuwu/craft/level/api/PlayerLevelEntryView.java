package emaki.jiuwu.craft.level.api;

public record PlayerLevelEntryView(String typeId,
        int level,
        double exp,
        double totalExp,
        double requiredExp,
        double progress) {

    public PlayerLevelEntryView {
        typeId = typeId == null ? "" : typeId;
    }
}
