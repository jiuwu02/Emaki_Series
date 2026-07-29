/**
 * Vanilla dialog access, reached through {@code EmakiCoreLibApi.dialogs()}.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.corelib.api.dialog.CoreLibDialogs} is
 * {@link org.jetbrains.annotations.ApiStatus.NonExtendable}. It replaces the former standalone
 * {@code DialogApi} static facade, which was removed so the module has a single entry point.
 *
 * <h2>Threading</h2>
 * {@code enabled()}, {@code dialogIds()}, and {@code contains(String)} may be called from any thread.
 * {@code show} and {@code close} must run on the thread that owns the target player; on Folia that
 * is the player's region thread.
 *
 * <h2>Degradation</h2>
 * When EmakiCoreLib or its dialog subsystem is unavailable, {@code enabled()} is {@code false},
 * {@code dialogIds()} is empty, and both operations return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}.
 *
 * <h2>Client requirement</h2>
 * Showing a dialog requires a client on Minecraft 1.21.6 or newer. Behaviour on older clients is
 * unverified.
 */
package emaki.jiuwu.craft.corelib.api.dialog;
