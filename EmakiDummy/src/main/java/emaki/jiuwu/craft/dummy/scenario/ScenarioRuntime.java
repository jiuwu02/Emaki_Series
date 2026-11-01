package emaki.jiuwu.craft.dummy.scenario;

public interface ScenarioRuntime {

    long currentTimeMillis();

    double eventCount(String type);

    boolean eventMatched(String type, String where, double sinceCount);

    boolean logContains(String fileGlob, String contains);

    boolean auditContains(String text);

    void ensureWatching(String type);
}
