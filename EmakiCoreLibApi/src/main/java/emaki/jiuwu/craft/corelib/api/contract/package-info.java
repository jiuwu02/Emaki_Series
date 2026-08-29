/**
 * Immutable status and result contracts shared by Emaki APIs.
 *
 * <p>Unavailability is explicit rather than encoded as a business value. New failure kinds may be
 * added, so exhaustive callers need a fallback. Depend on API artifacts as {@code provided} or
 * {@code compileOnly}; do not shade them.
 */
package emaki.jiuwu.craft.corelib.api.contract;
