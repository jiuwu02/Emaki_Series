/**
 * Action pipeline runtime.
 *
 * <p>Layout:</p>
 * <ul>
 *   <li>{@code pipeline} — context, argument view, placeholder seam;</li>
 *   <li>{@code pipeline.registry} — the single stage registry (requirement R1) with its three tables;</li>
 *   <li>{@code pipeline.compile} — pipeline text to AST, at config load time;</li>
 *   <li>{@code pipeline.exec} — AST interpretation on the hot path;</li>
 *   <li>{@code action.builtin} — builtin sources, gates and actions.</li>
 * </ul>
 */
package emaki.jiuwu.craft.corelib.action.pipeline;
