package emaki.jiuwu.craft.cooking.model;

public enum StationType {

    CHOPPING_BOARD("chopping_board", "砧板", "ChoppingBoard"),
    WOK("wok", "炒锅", "Wok"),
    GRINDER("grinder", "研磨机", "Grinder"),
    STEAMER("steamer", "蒸锅", "Steamer");

    private final String folderName;
    private final String displayName;
    private final String legacySection;

    StationType(String folderName, String displayName, String legacySection) {
        this.folderName = folderName;
        this.displayName = displayName;
        this.legacySection = legacySection;
    }

    public String folderName() {
        return folderName;
    }

    public String displayName() {
        return displayName;
    }

    public String legacySection() {
        return legacySection;
    }
}
