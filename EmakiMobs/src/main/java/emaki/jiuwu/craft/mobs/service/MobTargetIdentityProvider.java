package emaki.jiuwu.craft.mobs.service;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentity;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentityProvider;
import emaki.jiuwu.craft.mobs.EmakiMobsPlugin;

public final class MobTargetIdentityProvider implements CoreTargetIdentityProvider {

    public static final String SYSTEM_ID = "emakimobs";

    private final EmakiMobsPlugin plugin;

    public MobTargetIdentityProvider(EmakiMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String systemId() {
        return SYSTEM_ID;
    }

    @Override
    public @Nullable CoreTargetIdentity identify(@Nullable LivingEntity entity) {
        if (entity == null || !plugin.contentReady()) {
            return null;
        }
        String mobId = plugin.mobIdentifier().readId(entity);
        return mobId != null && plugin.mobRegistry().get().containsKey(mobId)
                ? new CoreTargetIdentity(SYSTEM_ID, mobId, 0D)
                : null;
    }
}