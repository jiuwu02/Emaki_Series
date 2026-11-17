package emaki.jiuwu.craft.corelib.log;

public enum LogTag implements LogTagType {

    ACTION("action"),
    ANIMATION("animation"),
    ASSEMBLY("assembly"),
    CHAT("chat"),
    COMMAND("command"),
    COMPONENT("component"),
    CONFIG("config"),
    DEBUG("debug"),
    DIALOG("dialog"),
    DISPLAY("display"),
    ECONOMY("economy"),
    EDITOR("editor"),
    EVENT("event"),
    GUI("gui"),
    INTEGRATION("integration"),
    ITEM_SOURCE("item-source"),
    JOURNAL("journal"),
    LOADER("loader"),
    MATCHER("matcher"),
    METRICS("metrics"),
    PERSISTENCE("persistence"),
    PIPELINE("pipeline"),
    REFRESH("refresh"),
    REGISTRY("registry"),
    RELOAD("reload"),
    SCHEDULER("scheduler"),
    SCRIPT("script"),
    SHUTDOWN("shutdown"),
    SPAWN("spawn"),
    STARTUP("startup"),
    TEXT("text"),
    TRIGGER("trigger");

    private final String key;

    LogTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
