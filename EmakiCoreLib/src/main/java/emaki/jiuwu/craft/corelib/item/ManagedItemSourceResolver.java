package emaki.jiuwu.craft.corelib.item;

interface ManagedItemSourceResolver extends ItemSourceResolver {

    String pluginName();

    String loadEventClassName();

    Status bootstrap();

    Status onPluginEnabled();

    Status onItemsLoaded();

    void onPluginDisabled();

    record Status(State state, String detail) {

        public Status {
            detail = detail == null ? "" : detail.trim();
        }
    }

    enum State {
        ABSENT,
        WAITING,
        READY,
        INCOMPATIBLE
    }
}
