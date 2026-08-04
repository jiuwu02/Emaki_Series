/**
 * Immutable value types exchanged across the EmakiStation API.
 *
 * <p>Every type here is a detached view: the values were true when the object was built and are never
 * updated in place. Callers that need current state re-query rather than holding on to a snapshot.
 */
package emaki.jiuwu.craft.station.api.model;
