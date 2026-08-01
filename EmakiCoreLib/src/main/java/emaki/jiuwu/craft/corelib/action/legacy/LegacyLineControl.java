package emaki.jiuwu.craft.corelib.action.legacy;

/**
 * The {@code @condition} / {@code @chance} / {@code @delay} / {@code @ignore_failure} prefixes an old
 * action line could carry.
 *
 * <p>Moved here from the removed v1 action package so the one-shot converter can still read old syntax.
 * It is package-private to the converter: nothing but the migration should be parsing this form, and
 * deleting the migration deletes this with it.</p>
 *
 * @param condition the {@code @condition} expression, {@code null} when absent
 * @param chance the {@code @chance} value, {@code null} when absent
 * @param delay the {@code @delay} value, {@code null} when absent
 * @param ignoreFailure whether {@code @ignore_failure} was present
 */
record LegacyLineControl(String condition,
        String chance,
        String delay,
        boolean ignoreFailure) {

    /** {@return a control block with no prefixes set} */
    static LegacyLineControl none() {
        return new LegacyLineControl(null, null, null, false);
    }
}
