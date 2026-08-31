package emaki.jiuwu.craft.corelib.display;

public interface DisplayLifetimeSpec {

    String groupKey();

    String runtimeKey();

    boolean hasLifetime();

    int lifetimeTicks();
}
