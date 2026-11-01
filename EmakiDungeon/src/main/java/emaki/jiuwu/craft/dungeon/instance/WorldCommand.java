package emaki.jiuwu.craft.dungeon.instance;

import java.util.UUID;

public interface WorldCommand {

    int blockCost();

    void execute() throws Exception;

    default void fail(Throwable throwable) {
    }

    default WorldCommandTarget target() {
        return WorldCommandTarget.GLOBAL;
    }

    default String worldName() {
        return null;
    }

    default double[] anchor() {
        return null;
    }

    default UUID entityId() {
        return null;
    }

    default void retired() {
    }
}
