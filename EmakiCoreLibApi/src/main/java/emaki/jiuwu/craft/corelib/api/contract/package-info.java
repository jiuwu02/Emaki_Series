/**
 * Shared result and status contract for every Emaki public API module.
 *
 * <h2>Stability</h2>
 * Stable. The three types in this package ({@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult},
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind},
 * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus}) are the foundation of all other Emaki
 * API modules and will not change incompatibly without a major version bump. New
 * {@code FailureKind} constants may be appended, so callers should keep a {@code default} branch.
 *
 * <h2>Threading</h2>
 * All types here are immutable value objects and are safe to read from any thread. Threading
 * requirements belong to the methods that produce them; each Emaki facade documents its own.
 *
 * <h2>Degradation</h2>
 * When a backing plugin is missing, disabled, or mid-reload, its facade returns
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} and its
 * {@code status()} returns {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()}.
 * No Emaki API method returns a business-shaped value to signal unavailability.
 *
 * <h2>Shading warning</h2>
 * Depend on Emaki API artifacts with {@code provided} (Maven) or {@code compileOnly} (Gradle).
 * Never shade them into your own jar: each Emaki runtime already embeds its own un-relocated copy,
 * and a second copy would silently break event listener registration.
 */
package emaki.jiuwu.craft.corelib.api.contract;
