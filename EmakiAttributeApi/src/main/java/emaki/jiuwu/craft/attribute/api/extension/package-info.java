/**
 * Third-party extension contracts for contributing values to EmakiAttribute, including provider values and
 * whole-item contribution gates.
 *
 * <h2>Stability</h2>
 * Provider value contracts are stable. The owner-scoped registration facade is experimental while its
 * lifecycle and replacement semantics are validated by third-party integrations.
 *
 * <h2>Threading</h2>
 * Registration and handle closure may occur from any thread. Provider callbacks run synchronously on the
 * owner thread of the entity whose snapshot is being collected and must remain fast and side-effect free.
 *
 * <h2>Degradation</h2>
 * When EmakiAttribute is unavailable, registration returns an inactive closeable handle and no provider is
 * invoked.
 */
package emaki.jiuwu.craft.attribute.api.extension;
