package emaki.jiuwu.craft.corelib.action;

public record ActionLineControl(String condition,
                                   String chance,
                                   String delay,
                                   boolean ignoreFailure) {

    public static ActionLineControl none() {
        return new ActionLineControl(null, null, null, false);
    }
}
