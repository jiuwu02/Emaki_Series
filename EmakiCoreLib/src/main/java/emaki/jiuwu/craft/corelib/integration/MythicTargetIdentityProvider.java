package emaki.jiuwu.craft.corelib.integration;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentity;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentityProvider;
import emaki.jiuwu.craft.corelib.api.integration.MythicMobBridge;

public final class MythicTargetIdentityProvider implements CoreTargetIdentityProvider {

    public static final String SYSTEM_ID = "mythicmobs";

    private final MythicMobBridge bridge;

    public MythicTargetIdentityProvider(MythicMobBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public @NotNull String systemId() {
        return SYSTEM_ID;
    }

    @Override
    public @Nullable CoreTargetIdentity identify(@Nullable LivingEntity entity) {
        MythicMobBridge.MythicMobSnapshot snapshot = bridge.snapshot(entity);
        if (snapshot == null) {
            return null;
        }
        return new CoreTargetIdentity(SYSTEM_ID, snapshot.mobId(), snapshot.level());
    }
}