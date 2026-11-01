package emaki.jiuwu.craft.dummy.transport;

public interface TransportLink extends AutoCloseable {

    boolean isActive();

    void sendChat(String text);

    void sendCommand(String command);

    void sendMovement(double x, double y, double z, float yaw, float pitch);

    void sendLook(float yaw, float pitch);

    void sendInteractBlock(int x, int y, int z, int face);

    int sendAttackNearest(double maxDistance);

    int clickSlot(int slot);

    double[] pose();

    boolean hasPose();

    void disconnect(String reason);

    @Override
    void close();
}
