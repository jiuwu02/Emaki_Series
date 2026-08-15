package emaki.jiuwu.craft.mobs.api.model;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable snapshot of a registered mob definition.
 *
 * @param id          the unique mob identifier used in YAML files and commands
 * @param entityType  the underlying Minecraft entity type
 * @param displayName the MiniMessage-formatted custom name, or {@code null} when unset
 * @param experience  experience override on kill; {@code 0} means use the vanilla default
 */
public record MobDefinition(
        String id,
        EntityType entityType,
        @Nullable String displayName,
        int experience
) {}
