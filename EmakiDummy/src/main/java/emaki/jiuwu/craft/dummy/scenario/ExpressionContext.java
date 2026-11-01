package emaki.jiuwu.craft.dummy.scenario;

public interface ExpressionContext {

    double onlineCount();

    double dummyOnline(String name);

    double eventCount(String type);

    boolean auditContains(String text);

    boolean recvContains(String name, String text);

    double inventoryCount(String name);
}
