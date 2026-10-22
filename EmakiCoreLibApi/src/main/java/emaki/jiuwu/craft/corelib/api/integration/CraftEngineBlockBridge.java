package emaki.jiuwu.craft.corelib.api.integration;

import org.jetbrains.annotations.ApiStatus;

/**
 * {@link CustomBlockBridge} specialization for the CraftEngine backend.
 *
 * <p>The distinct type lets consumers request the CraftEngine bridge explicitly (for example to
 * drive the {@code lit} property of CraftEngine stove blocks) instead of iterating every
 * registered provider. It adds no members beyond {@link CustomBlockBridge} today; when
 * CraftEngine is absent the provider still hands out an instance of this type whose methods all
 * answer the neutral result. Marked experimental: the type may still change between releases.
 */
@ApiStatus.Experimental
public interface CraftEngineBlockBridge extends CustomBlockBridge {
}
