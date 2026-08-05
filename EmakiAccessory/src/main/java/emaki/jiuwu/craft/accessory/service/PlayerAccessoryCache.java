package emaki.jiuwu.craft.accessory.service;

import java.util.UUID;

import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.corelib.session.AbstractPlayerSessionCache;

/**
 * Session cache for loaded player accessory contents.
 *
 * <p>All generation, seal, dirty and save-lane mechanics live in {@link AbstractPlayerSessionCache};
 * this subclass only binds the key and payload types. Reusing the shared base is deliberate: the two
 * problems it already solves - a stale async load overwriting a newer session, and writes continuing
 * after shutdown - are exactly the ones accessory storage would otherwise have to solve again.
 */
public final class PlayerAccessoryCache extends AbstractPlayerSessionCache<UUID, PlayerAccessories> {
}
