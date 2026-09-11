package emaki.jiuwu.craft.mobs.model;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

public final class NoopModelBridge implements MobModelBridge {

    public static final NoopModelBridge INSTANCE = new NoopModelBridge();

    private NoopModelBridge() {
    }

    @Override
    public @NotNull String id() {
        return "none";
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public boolean hasBlueprint(@NotNull String blueprintId) {
        return false;
    }

    @Override
    public boolean attach(@NotNull Entity entity, @NotNull String blueprintId, double scale) {
        return false;
    }

    @Override
    public void playAnimation(@NotNull Entity entity, @NotNull String animationName, double speed, int fadeTicks,
            boolean loop) {
    }

    @Override
    public void stopAnimation(@NotNull Entity entity, @NotNull String animationName) {
    }

    @Override
    public void applyLod(@NotNull Entity entity, @NotNull LodTier tier, double viewDistance) {
    }

    @Override
    public void detach(@NotNull Entity entity) {
    }

    @Override
    public void close() {
    }
}
