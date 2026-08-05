package emaki.jiuwu.craft.accessory.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.accessory.model.AccessoryContributionSnapshot;
import emaki.jiuwu.craft.accessory.service.AccessoryContributionService;
import emaki.jiuwu.craft.attribute.api.extension.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.extension.AttributeContributionProvider;

/**
 * Feeds accessory attributes into EmakiAttribute.
 *
 * <p>Deliberately trivial: {@link #collect(LivingEntity)} is a map read over an already-built snapshot.
 * EmakiAttribute invokes it while computing its combat-snapshot cache signature, which happens on every
 * combat snapshot read and before the cache decision, so a cache hit does not avoid this call. Any
 * parsing here would land directly on the combat hot path.
 *
 * <p>Because contributions are hashed into that signature, a changed accessory automatically invalidates
 * EmakiAttribute's cache. This module therefore needs no attribute-side invalidation hooks on join,
 * respawn or world change, which is what prevents the "accessory still in the slot but its stats are
 * gone" failure reported against comparable mods.
 *
 * <p>Exceptions are swallowed on purpose. EmakiAttribute's provider loop has no {@code try/catch} of its
 * own, so an exception escaping here would corrupt an entire combat snapshot rather than just drop this
 * module's contribution.
 *
 * <p>The provider id carries a namespace because EmakiAttribute keys contribution providers globally
 * rather than per owner: an unqualified id could be superseded by another plugin's registration.
 */
public final class AccessoryAttributeProvider implements AttributeContributionProvider {

    private static final String PROVIDER_ID = "emakiaccessory:accessories";

    private final AccessoryContributionService contributionService;
    private final Logger logger;

    /**
     * Creates the provider.
     *
     * @param contributionService the snapshot cache to read
     * @param logger              receives the one-line report when a collection fails
     */
    public AccessoryAttributeProvider(AccessoryContributionService contributionService, Logger logger) {
        this.contributionService = contributionService;
        this.logger = logger;
    }

    @Override
    public @NotNull String id() {
        return PROVIDER_ID;
    }

    @Override
    public int priority() {
        // Neutral: accessory attributes are plain additive contributions with no ordering requirement
        // against other providers.
        return 100;
    }

    @Override
    public @NotNull Collection<AttributeContribution> collect(@NotNull LivingEntity entity) {
        try {
            if (!(entity instanceof Player player)) {
                return List.of();
            }
            AccessoryContributionSnapshot snapshot = contributionService.snapshot(player.getUniqueId());
            Map<String, Double> attributes = snapshot.attributes();
            if (attributes.isEmpty()) {
                return List.of();
            }
            List<AttributeContribution> contributions = new ArrayList<>(attributes.size());
            attributes.forEach((attributeId, value) -> contributions.add(
                    new AttributeContribution(attributeId, value, AccessoryContributionService.SOURCE_ACCESSORY)));
            return contributions;
        } catch (RuntimeException exception) {
            if (logger != null) {
                logger.warning("Accessory attribute collection failed: " + exception.getMessage());
            }
            return List.of();
        }
    }
}
