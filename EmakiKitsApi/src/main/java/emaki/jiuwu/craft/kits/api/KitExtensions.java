package emaki.jiuwu.craft.kits.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Reserved extension layer for EmakiKits.
 *
 * <p>The current runtime exposes no owner-scoped third-party registration point that can be
 * surfaced without inventing a second kit pipeline. The layer exists so the facade keeps the common
 * {@code catalog / operations / extensions} shape and can grow compatibly when a real extension
 * point is introduced.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface KitExtensions {
}
