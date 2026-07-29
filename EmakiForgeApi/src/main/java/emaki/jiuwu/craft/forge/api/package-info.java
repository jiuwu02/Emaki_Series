/**
 * Public API for the EmakiForge forging system.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.forge.api.EmakiForgeApi} is the entry point;
 * {@link emaki.jiuwu.craft.forge.api.ForgeCatalog} and
 * {@link emaki.jiuwu.craft.forge.api.ForgeOperations} are
 * {@link org.jetbrains.annotations.ApiStatus.NonExtendable}.
 *
 * <h2>Threading</h2>
 * Catalog lookups that do not involve a player may be called from any thread. Everything that takes a
 * {@code Player} — recipe matching, validation, GUI opening, inventory refresh — must run on that
 * player's owner thread and otherwise reports
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD}.
 *
 * <h2>What is not exposed, and why</h2>
 * <ul>
 * <li><strong>Programmatic forge execution.</strong> EmakiForge's execution call requires a prepared
 * attempt, a runtime generation token, and delivery claim/rollback/commit callbacks that only its GUI
 * session layer can supply. It also fires no events outside that path. Drive forging through
 * {@link emaki.jiuwu.craft.forge.api.ForgeOperations#openForgeGui(org.bukkit.entity.Player, java.lang.String)}.</li>
 * <li><strong>Result preview.</strong> The underlying preview call is an unimplemented stub in the
 * current runtime, so no preview capability is offered rather than one that always yields nothing.</li>
 * <li><strong>Crafting mastery.</strong> The mastery service is package-private with no reachable
 * call path in the current runtime.</li>
 * </ul>
 *
 * <h2>Degradation</h2>
 * When EmakiForge is absent, {@code status()} reports
 * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()}, catalog queries answer
 * empty, and operations return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}.
 *
 * <h2>Do not shade</h2>
 * Use {@code provided} or {@code compileOnly}. EmakiForge's jar already carries an un-relocated copy
 * of these classes; a duplicate would make your event listeners silently unreachable.
 */
package emaki.jiuwu.craft.forge.api;
