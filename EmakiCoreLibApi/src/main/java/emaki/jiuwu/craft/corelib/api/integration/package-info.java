/**
 * Bridges for third-party custom-block providers.
 *
 * <h2>Stability</h2>
 * Experimental. These interfaces track upstream custom-block plugins whose own APIs change without
 * notice, so signatures here may follow. Implement them only if you provide custom blocks that Emaki
 * modules should recognise.
 *
 * <h2>Threading</h2>
 * Implementations are queried from block interaction and world-scan paths, which run on the owner
 * thread of the block's region. Keep implementations non-blocking and do not schedule from them.
 *
 * <h2>Degradation</h2>
 * When no bridge is registered, Emaki treats blocks as vanilla. A bridge that throws is treated as
 * absent for that call rather than propagating the failure into gameplay code.
 */
package emaki.jiuwu.craft.corelib.api.integration;
