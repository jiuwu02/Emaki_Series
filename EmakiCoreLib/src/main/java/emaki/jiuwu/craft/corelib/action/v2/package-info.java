/**
 * Action pipeline v2 runtime.
 *
 * <p>Coexists with the v1 {@code emaki.jiuwu.craft.corelib.action} package during the migration. v1
 * keeps serving existing configuration until every module has moved; nothing here modifies v1
 * behaviour.</p>
 *
 * <p>Layout:</p>
 * <ul>
 *   <li>{@code v2} — context, argument view, placeholder seam;</li>
 *   <li>{@code v2.registry} — the single stage registry (requirement R1) with its three tables;</li>
 *   <li>{@code v2.compile} — pipeline text to AST, at config load time;</li>
 *   <li>{@code v2.exec} — AST interpretation on the hot path;</li>
 *   <li>{@code v2.builtin} — builtin sources, gates and actions.</li>
 * </ul>
 */
package emaki.jiuwu.craft.corelib.action.v2;
