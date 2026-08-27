package emaki.jiuwu.craft.mobs.loader;

import emaki.jiuwu.craft.mobs.api.model.MobDefinition;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record MobSpec(
        String id,
        EntityType entityType,
        String displayName,
        Map<String, Object> components,
        Map<String, Double> eaAttributes,
        Map<String, List<String>> actions,
        int experience,
        boolean typeOverride,
        @Nullable ThreatConfig threatConfig,
        @Nullable BossBarConfig bossBarConfig
) {
    public MobDefinition toApiModel() {
        return new MobDefinition(id, entityType, displayName, experience);
    }
}
