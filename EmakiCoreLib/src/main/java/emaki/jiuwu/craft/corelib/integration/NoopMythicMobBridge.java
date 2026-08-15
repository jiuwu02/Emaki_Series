package emaki.jiuwu.craft.corelib.integration;

import org.bukkit.entity.LivingEntity;

import emaki.jiuwu.craft.corelib.api.integration.MythicMobBridge;

final class NoopMythicMobBridge implements MythicMobBridge {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public MythicMobSnapshot snapshot(LivingEntity entity) {
        return null;
    }
}
