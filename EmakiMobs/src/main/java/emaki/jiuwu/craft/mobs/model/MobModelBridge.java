package emaki.jiuwu.craft.mobs.model;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

public interface MobModelBridge {

    @NotNull String id();

    boolean available();

    boolean hasBlueprint(@NotNull String blueprintId);

    boolean attach(@NotNull Entity entity, @NotNull String blueprintId, double scale);

    void playAnimation(@NotNull Entity entity, @NotNull String animationName, double speed, int fadeTicks,
            boolean loop);

    void stopAnimation(@NotNull Entity entity, @NotNull String animationName);

    void applyLod(@NotNull Entity entity, @NotNull LodTier tier, double viewDistance);

    void detach(@NotNull Entity entity);

    void close();

    enum LodTier {
        NEAR,
        MID,
        FAR
    }
}
