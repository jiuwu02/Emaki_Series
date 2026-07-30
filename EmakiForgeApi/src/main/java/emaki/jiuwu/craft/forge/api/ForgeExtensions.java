package emaki.jiuwu.craft.forge.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Reserved extension layer for EmakiForge.
 *
 * <p>The current runtime has no owner-scoped third-party registration point that can be exposed
 * without inventing a second forging pipeline. The layer nevertheless exists so the facade keeps the
 * common {@code catalog / operations / extensions} shape and can grow compatibly when a real runtime
 * extension point is introduced.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ForgeExtensions {
}
