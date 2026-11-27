package emaki.jiuwu.craft.corelib.api.condition;

public enum CoreConditionOutcome {

    PASS,

    FAIL,

    UNKNOWN;

    public boolean passed() {
        return this == PASS;
    }
}
