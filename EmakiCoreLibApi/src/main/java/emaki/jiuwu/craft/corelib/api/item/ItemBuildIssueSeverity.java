package emaki.jiuwu.craft.corelib.api.item;

/**
 * Severity of one {@link ItemBuildIssue} reported while an item is created or patched.
 *
 * <p>Every issue carries exactly one of the two levels; there is deliberately no info level,
 * because the build only records findings that the operator should see. Whether a result counts
 * as successful is decided by {@link ItemBuildResult#success()}, which fails on any
 * {@link #ERROR} but tolerates {@link #WARNING}.
 */
public enum ItemBuildIssueSeverity {
    /** The item was still built, but part of the definition was questionable and may not behave as intended. */
    WARNING,
    /** The build could not produce the requested item; any ERROR makes the result unsuccessful. */
    ERROR
}
