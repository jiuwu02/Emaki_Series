package emaki.jiuwu.craft.cooking.model;

public enum StationType {

    CHOPPING_BOARD("chopping_board", "砧板"),
    WOK("wok", "炒锅"),
    GRINDER("grinder", "研磨机"),
    STEAMER("steamer", "蒸锅");

    private final String folderName;
    private final String displayName;

    StationType(String folderName, String displayName) {
        this.folderName = folderName;
        this.displayName = displayName;
    }

    public String folderName() {
        return folderName;
    }

    public String displayName() {
        return displayName;
    }
}
