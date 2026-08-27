package emaki.jiuwu.craft.mobs.provider;

import emaki.jiuwu.craft.attribute.api.extension.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.extension.AttributeContributionProvider;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class MobAttributeProvider implements AttributeContributionProvider {

    private static final String PROVIDER_ID = "emakimobs:definition";
    private static final int PRIORITY = 100;

    private final MobIdentifier mobIdentifier;
    private final Supplier<Map<String, MobSpec>> registry;

    public MobAttributeProvider(MobIdentifier mobIdentifier,
                                Supplier<Map<String, MobSpec>> registry) {
        this.mobIdentifier = mobIdentifier;
        this.registry = registry;
    }

    @Override
    public @NotNull String id() {
        return PROVIDER_ID;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public @NotNull Collection<AttributeContribution> collect(@NotNull LivingEntity entity) {
        if (entity instanceof Player) {
            return List.of();
        }
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) {
            return List.of();
        }
        MobSpec spec = registry.get().get(mobId);
        if (spec == null || spec.eaAttributes().isEmpty()) {
            return List.of();
        }
        String sourceId = PROVIDER_ID + ":" + mobId;
        List<AttributeContribution> contributions = new ArrayList<>(spec.eaAttributes().size());
        for (Map.Entry<String, Double> attribute : spec.eaAttributes().entrySet()) {
            Double value = attribute.getValue();
            if (value == null || Math.abs(value) <= 1.0E-9D) {
                continue;
            }
            contributions.add(new AttributeContribution(attribute.getKey(), value, sourceId));
        }
        return contributions;
    }
}
