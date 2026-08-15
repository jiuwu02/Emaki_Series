package emaki.jiuwu.craft.corelib.action.legacy;

record LegacyLineControl(String condition,
        String chance,
        String delay,
        boolean ignoreFailure) {

    static LegacyLineControl none() {
        return new LegacyLineControl(null, null, null, false);
    }
}
