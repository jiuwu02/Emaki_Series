package emaki.jiuwu.craft.dummy.transport;

public interface TransportCallbacks {

    default void onProtocolPacket(String direction, String packetName) {
    }

    default void onRecvEvent(String kind, String detail) {
    }

    default void onWindowOpened(int windowId, String title) {
    }

    default void onWindowContent(int windowId, int stateId, int itemCount) {
    }

    default void onWindowClosed(int windowId) {
    }

    default void onJoined(int entityId) {
    }

    default void onTeleport(double x, double y, double z, float yaw, float pitch) {
    }

    default void onServerMessage(String text, boolean overlay) {
    }

    default void onKicked(String reason) {
    }

    default void onClosed(String reason) {
    }
}
