package emaki.jiuwu.craft.mobs.api;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import org.bukkit.entity.LivingEntity;

public final class MobActionKeys {

    /** The living entity that attacked a managed mob for damage-take triggers. */
    public static final CoreActionKey<LivingEntity> ATTACKER =
            CoreActionKey.of("emakimobs:attacker", LivingEntity.class);

    /** The living entity that killed a managed mob for death triggers. */
    public static final CoreActionKey<LivingEntity> KILLER =
            CoreActionKey.of("emakimobs:killer", LivingEntity.class);

    /** The living entity killed by a managed mob for kill triggers. */
    public static final CoreActionKey<LivingEntity> VICTIM =
            CoreActionKey.of("emakimobs:victim", LivingEntity.class);

    /** The living entity selected as a managed mob's target for target triggers. */
    public static final CoreActionKey<LivingEntity> TARGET =
            CoreActionKey.of("emakimobs:target", LivingEntity.class);

    private MobActionKeys() {
        throw new UnsupportedOperationException("Utility class");
    }
}
