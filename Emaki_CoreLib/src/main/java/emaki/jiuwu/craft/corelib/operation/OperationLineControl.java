package emaki.jiuwu.craft.corelib.operation;

public record OperationLineControl(String condition,
                                   String chance,
                                   String delay,
                                   boolean ignoreFailure) {

    public static OperationLineControl none() {
        return new OperationLineControl(null, null, null, false);
    }
}
