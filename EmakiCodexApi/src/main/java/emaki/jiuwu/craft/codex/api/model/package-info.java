/**
 * Immutable value objects returned by the EmakiCodex API.
 *
 * <h2>Stability</h2>
 * Stable. Every type normalises its reference components in the canonical constructor, so no accessor
 * returns {@code null} except {@link emaki.jiuwu.craft.codex.api.model.AdvancementView#parentKey()}, which
 * is genuinely absent for a page's root advancement.
 *
 * <h2>Text is MiniMessage, not plain</h2>
 * {@code title()} and {@code description()} carry MiniMessage markup exactly as the server owner authored
 * it. Parse them before display; do not assume they are plain text.
 *
 * <h2>Two kinds of identifier</h2>
 * {@link emaki.jiuwu.craft.codex.api.model.AdvancementView#key()} is the fully qualified
 * {@code namespace:path} form that Minecraft itself uses, and is what you persist or compare.
 * {@code nodeId()} is the short local id within a page, useful for display but not unique across pages.
 *
 * <h2>What is not exposed</h2>
 * Advancement tree coordinates, icon source strings, completion action scripts, and trigger conditions are
 * all configuration internals. Background texture paths are client-side resource references with no
 * server-side meaning.
 *
 * <h2>Threading</h2>
 * All types are safe to read from any thread.
 *
 * <h2>Degradation</h2>
 * These values are returned by a usable Codex facade. When EmakiCodex is unavailable, catalog queries are
 * empty and operations return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} instead of a synthetic view.
 */
package emaki.jiuwu.craft.codex.api.model;
