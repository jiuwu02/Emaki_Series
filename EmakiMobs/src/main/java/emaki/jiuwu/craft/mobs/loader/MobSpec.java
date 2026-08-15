package emaki.jiuwu.craft.mobs.loader;

import emaki.jiuwu.craft.mobs.api.model.MobDefinition;
import org.bukkit.entity.EntityType;

import java.util.Map;

public record MobSpec(
        String id,
        EntityType entityType,
        String displayName,
        Map<String, Object> components,
        Map<String, Double> attributes,
        int experience
) {
    public MobDefinition toApiModel() {
        return new MobDefinition(id, entityType, displayName, experience);
    }
}
