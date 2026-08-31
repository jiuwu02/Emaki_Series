package emaki.jiuwu.craft.corelib.integration;

import java.util.Optional;

import org.bukkit.entity.LivingEntity;

import emaki.jiuwu.craft.corelib.api.integration.MythicMobBridge;
import emaki.jiuwu.craft.corelib.api.text.Texts;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.skills.placeholders.PlaceholderString;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;

final class MythicMobBridgeApi implements MythicMobBridge {

    @Override
    public boolean available() {
        try {
            return MythicBukkit.inst() != null;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Override
    public MythicMobSnapshot snapshot(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        try {
            Optional<ActiveMob> activeMob = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
            return activeMob.map(this::toSnapshot).orElse(null);
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private MythicMobSnapshot toSnapshot(ActiveMob activeMob) {
        return new MythicMobSnapshot(
                Texts.toStringSafe(activeMob.getMobType()),
                activeMob.getLevel(),
                resolveDisplayName(activeMob));
    }

    private String resolveDisplayName(ActiveMob activeMob) {
        String activeName = Texts.trim(activeMob.getDisplayName());
        if (Texts.isNotBlank(activeName)) {
            return activeName;
        }
        MythicMob type = activeMob.getType();
        if (type == null) {
            return "";
        }
        PlaceholderString configured = type.getDisplayName();
        if (configured == null) {
            return "";
        }
        String resolved = Texts.trim(configured.get());
        return Texts.isBlank(resolved) ? "" : resolved;
    }
}
