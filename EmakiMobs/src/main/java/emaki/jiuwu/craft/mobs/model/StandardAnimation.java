package emaki.jiuwu.craft.mobs.model;

import emaki.jiuwu.craft.corelib.api.animation.AnimationPriority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum StandardAnimation {

    IDLE("idle", AnimationPriority.AMBIENT),
    WALK("walk", AnimationPriority.AMBIENT),
    RUN("run", AnimationPriority.AMBIENT),
    INTERACT("interact", AnimationPriority.UTILITY),
    SKILL("skill", AnimationPriority.ACTION),
    ATTACK("attack", AnimationPriority.ACTION),
    HURT("hurt", AnimationPriority.REACTIVE),
    DEATH("death", AnimationPriority.TERMINAL);

    private final String defaultAnimation;
    private final AnimationPriority priority;

    StandardAnimation(String defaultAnimation, AnimationPriority priority) {
        this.defaultAnimation = defaultAnimation;
        this.priority = priority;
    }

    public @NotNull String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public @NotNull String defaultAnimation() {
        return defaultAnimation;
    }

    public @NotNull AnimationPriority priority() {
        return priority;
    }

    public boolean loops() {
        return this == IDLE || this == WALK || this == RUN;
    }

    public static @Nullable StandardAnimation fromTrigger(@NotNull String triggerName) {
        return switch (triggerName) {
            case "on_death" -> DEATH;
            case "on_damage_take" -> HURT;
            case "on_damage_give" -> ATTACK;
            default -> null;
        };
    }
}
