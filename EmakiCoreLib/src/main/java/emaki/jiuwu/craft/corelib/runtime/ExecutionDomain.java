package emaki.jiuwu.craft.corelib.runtime;

/**
 * Explicit execution domains used by CoreLib scheduling and lifecycle boundaries.
 */
public enum ExecutionDomain {
    SERVER_GLOBAL(false, false),
    LOCATION_REGION(true, false),
    ENTITY(true, false),
    ASYNC_COMPUTE(false, true),
    PHYSICAL_FILE(false, true);

    private final boolean ownershipBound;
    private final boolean asynchronous;

    ExecutionDomain(boolean ownershipBound, boolean asynchronous) {
        this.ownershipBound = ownershipBound;
        this.asynchronous = asynchronous;
    }

    public boolean ownershipBound() {
        return ownershipBound;
    }

    public boolean asynchronous() {
        return asynchronous;
    }
}
