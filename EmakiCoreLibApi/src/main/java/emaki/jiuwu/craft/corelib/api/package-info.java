/**
 * Public API facade for the shared EmakiCoreLib runtime core.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi} is the single entry point;
 * {@code install} and {@code uninstall} are marked
 * {@link org.jetbrains.annotations.ApiStatus.Internal} and belong to EmakiCoreLib's lifecycle only.
 * The {@code Bridge} interface is {@link org.jetbrains.annotations.ApiStatus.NonExtendable}.
 *
 * <h2>Scope</h2>
 * This facade serves third-party plugins only. The ten Emaki business modules link EmakiCoreLib's
 * implementation classes directly through {@code join-classpath: true}, so this package is not an
 * isolation layer and will not grow into one. Text rendering, YAML services, GUI infrastructure, the
 * expression engine, language loading, bootstrap, condition evaluation, PDC services, lifecycle
 * coordinators, economy, the internal event bus, the placeholder registry, and the assembly
 * subsystem stay internal.
 *
 * <h2>Threading</h2>
 * Item display name resolution, configured-item building, and every action-registry query may be
 * called from any thread. Anything that touches a player or a block must run on that object's owner
 * thread; use {@link emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling} to hop there.
 *
 * <h2>Degradation</h2>
 * {@link emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi#status()} never returns {@code null}. The
 * {@code dialogs()} and {@code scheduling()} accessors return no-op implementations instead of
 * {@code null}, operations return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}, and configured-item
 * building reports unavailability as an error issue on
 * {@link emaki.jiuwu.craft.corelib.api.item.ItemBuildResult}.
 *
 * <h2>Do not shade</h2>
 * Use {@code provided} (Maven) or {@code compileOnly} (Gradle). EmakiCoreLib's jar already contains
 * an un-relocated copy of these classes; a second copy silently breaks event listener registration.
 */
package emaki.jiuwu.craft.corelib.api;
