package emaki.jiuwu.craft.storage.api.model;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;

/**
 * Opaque ticket for one held reservation.
 *
 * <p>It carries identifiers only and deliberately holds no reference to a runtime object. A handle
 * that pointed at an implementation instance would keep the retired instance alive across a reload
 * and let a stale caller commit against a table that no longer exists.
 *
 * @param reservationId the reservation identity, unique per player storage
 * @param playerId      the storage owner the reservation belongs to
 */
public record ReservationHandle(@NotNull UUID reservationId, @NotNull UUID playerId) {
}
