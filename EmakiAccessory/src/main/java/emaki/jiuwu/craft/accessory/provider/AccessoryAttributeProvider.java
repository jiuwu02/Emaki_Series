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

public final class AccessoryAttributeProvider implements AttributeContributionProvider {

    private static final String PROVIDER_ID = "emakiaccessory:accessories";

    private final AccessoryContributionService contributionService;
    private final Logger logger;

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
