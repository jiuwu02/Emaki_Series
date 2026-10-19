package emaki.jiuwu.craft.dungeon.instance;

public interface WorldCommand {

    int blockCost();

    void execute() throws Exception;

    default void fail(Throwable throwable) {
    }
}
