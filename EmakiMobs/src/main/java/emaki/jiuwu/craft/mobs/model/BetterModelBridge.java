package emaki.jiuwu.craft.mobs.model;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import kr.toxicity.model.api.tracker.ModelScaler;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

public final class BetterModelBridge implements MobModelBridge {

    @Override
    public @NotNull String id() {
        return "better_model";
    }

    @Override
    public boolean available() {
        try {
            return !BetterModel.modelKeys().isEmpty() || BetterModel.platform() != null;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Override
    public boolean hasBlueprint(@NotNull String blueprintId) {
        try {
            return BetterModel.model(blueprintId).isPresent();
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Override
    public boolean attach(@NotNull Entity entity, @NotNull String blueprintId, double scale) {
        ModelRenderer renderer = BetterModel.model(blueprintId).orElse(null);
        if (renderer == null) {
            return false;
        }
        EntityTracker tracker = renderer.getOrCreate(BukkitAdapter.adapt(entity));
        if (tracker == null) {
            return false;
        }
        if (scale > 0) {
            tracker.scaler(ModelScaler.value((float) scale));
        }
        return true;
    }

    @Override
    public void playAnimation(@NotNull Entity entity, @NotNull String animationName, double speed, int fadeTicks,
            boolean loop) {
        EntityTracker tracker = tracker(entity);
        if (tracker == null) {
            return;
        }
        int blend = Math.max(0, fadeTicks);
        AnimationModifier modifier = AnimationModifier.builder()
                .start(blend)
                .end(blend)
                .speed((float) speed)
                .type(loop ? AnimationIterator.Type.LOOP : AnimationIterator.Type.PLAY_ONCE)
                .build();
        tracker.animate(animationName, modifier);
    }

    @Override
    public void stopAnimation(@NotNull Entity entity, @NotNull String animationName) {
        EntityTracker tracker = tracker(entity);
        if (tracker != null) {
            tracker.stopAnimation(animationName);
        }
    }

    @Override
    public void applyLod(@NotNull Entity entity, @NotNull LodTier tier, double viewDistance) {
        EntityTracker tracker = tracker(entity);
        if (tracker == null) {
            return;
        }
        tracker.pause(tier == LodTier.FAR);
    }

    @Override
    public void detach(@NotNull Entity entity) {
        EntityTrackerRegistry registry = BetterModel.registryOrNull(entity.getUniqueId());
        if (registry == null) {
            return;
        }
        for (EntityTracker tracker : registry.trackers()) {
            tracker.despawn();
        }
    }

    @Override
    public void close() {
    }

    private EntityTracker tracker(Entity entity) {
        EntityTrackerRegistry registry = BetterModel.registryOrNull(entity.getUniqueId());
        if (registry == null) {
            return null;
        }
        return registry.first();
    }
}
