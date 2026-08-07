/**
 * Readiness registry: wait until another Emaki module has finished loading its data.
 *
 * <h2>Why waiting is necessary at all</h2>
 * {@code softdepend} and {@code depend} only order plugin enable calls. They say nothing about work
 * a module starts inside {@code onEnable} and finishes later: a module that loads its definitions
 * asynchronously and publishes them through the scheduler cannot have them ready before the next
 * tick, and during server startup the main thread is still inside the plugin enable loop, so no tick
 * has run yet. A consumer that reads {@code status().usable()} in its own {@code onEnable} therefore
 * observes {@code ready == false} structurally, not intermittently.
 *
 * <h2>Why it lives in CoreLib</h2>
 * A probe cannot live in the plugin being probed. Calling {@code EmakiItemApi.whenReady(...)} on a
 * build whose shaded API predates that method throws {@link java.lang.NoSuchMethodError} &mdash; the
 * probe is itself the thing that needed probing. EmakiCoreLib is a hard dependency of every Emaki
 * module ({@code required: true, load: BEFORE}), so a registry placed here is always present, and a
 * consumer only has to know one class to wait for any module.
 *
 * <h2>Publishing</h2>
 * Each module publishes its own state: {@code markLoading} before it starts loading,
 * {@code markReady} at the point its data is actually usable, {@code markAbsent} in
 * {@code onDisable}. CoreLib cannot derive this itself because it must not depend on the business
 * API modules.
 *
 * <h2>Consuming</h2>
 * Call {@code EmakiCoreLibApi.whenReady(this, "EmakiItem", this::buildIndex)} in {@code onEnable}
 * and close the returned
 * {@link emaki.jiuwu.craft.corelib.api.readiness.ReadinessRegistration} in {@code onDisable}. The
 * module name is a plain string on purpose: referencing a constant from the provider's API jar drags
 * that class into the constant pool, which is exactly the class-loading failure the capability
 * registry documents. When the module is already ready the callback runs synchronously inside the
 * registration call, so there is no window in which a consumer can miss the signal. The callback fires
 * once; re-check at the point of use rather than caching what it observed, because a reload replaces
 * the data it read.
 *
 * <h2>Thread</h2>
 * The callback runs on whichever thread set the state. For a module that loads asynchronously and
 * publishes on the server thread that is the server thread; for a module that reloads synchronously
 * it is the thread that called reload. Schedule explicitly before touching Bukkit state.
 *
 * <h2>Degradation</h2>
 * With EmakiCoreLib absent, {@code whenReady} returns
 * {@link emaki.jiuwu.craft.corelib.api.readiness.ReadinessRegistration#inactive()}, the callback
 * never runs and {@code isModuleReady} is {@code false}. Nothing returns {@code null} and nothing
 * throws.
 */
package emaki.jiuwu.craft.corelib.api.readiness;
