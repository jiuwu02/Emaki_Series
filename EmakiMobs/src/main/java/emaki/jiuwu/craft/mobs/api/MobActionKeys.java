package emaki.jiuwu.craft.mobs.api;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import org.bukkit.entity.LivingEntity;

public final class MobActionKeys {

    public static final CoreActionKey<LivingEntity> ATTACKER = 
            CoreActionKey.of("emakimobs:attacker", LivingEntity.class);

    public static final CoreActionKey<LivingEntity> KILLER = 
            CoreActionKey.of("emakimobs:killer", LivingEntity.class);

    public static final CoreActionKey<LivingEntity> VICTIM = 
            CoreActionKey.of("emakimobs:victim", LivingEntity.class);

    public static final CoreActionKey<LivingEntity> TARGET = 
            CoreActionKey.of("emakimobs:target", LivingEntity.class);

    private MobActionKeys() {
        throw new UnsupportedOperationException("Utility class");
    }
}
