package emaki.jiuwu.craft.mobs.model;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

public final class ModelEngineBridge implements MobModelBridge {

    @Override
    public @NotNull String id() {
        return "model_engine";
    }

    @Override
    public boolean available() {
        try {
            return ModelEngineAPI.getAPI() != null;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Override
    public boolean hasBlueprint(@NotNull String blueprintId) {
        try {
            return ModelEngineAPI.getBlueprint(blueprintId) != null;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Override
    public boolean attach(@NotNull Entity entity, @NotNull String blueprintId, double scale) {
        ModeledEntity modeled = ModelEngineAPI.getOrCreateModeledEntity(entity);
        if (modeled == null) {
            return false;
        }
        ActiveModel activeModel = ModelEngineAPI.createActiveModel(blueprintId);
        if (activeModel == null) {
            return false;
        }
        if (scale > 0) {
            activeModel.setScale(scale);
        }
        return modeled.addModel(activeModel, true).isPresent();
    }

    @Override
    public void playAnimation(@NotNull Entity entity, @NotNull String animationName, double speed, int fadeTicks,
            boolean loop) {
        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(entity);
        if (modeled == null) {
            return;
        }
        double fadeSeconds = Math.max(0.0D, fadeTicks / 20.0D);
        for (ActiveModel activeModel : modeled.getModels().values()) {
            AnimationHandler handler = activeModel.getAnimationHandler();
            if (handler != null) {
                handler.playAnimation(animationName, speed, 0.0D, fadeSeconds, loop);
            }
        }
    }

    @Override
    public void stopAnimation(@NotNull Entity entity, @NotNull String animationName) {
        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(entity);
        if (modeled == null) {
            return;
        }
        for (ActiveModel activeModel : modeled.getModels().values()) {
            AnimationHandler handler = activeModel.getAnimationHandler();
            if (handler != null) {
                handler.stopAnimation(animationName);
            }
        }
    }

    @Override
    public void applyLod(@NotNull Entity entity, @NotNull LodTier tier, double viewDistance) {
        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(entity);
        if (modeled == null) {
            return;
        }
        float range = tier == LodTier.FAR ? 0.0F : (float) viewDistance;
        for (ActiveModel activeModel : modeled.getModels().values()) {
            activeModel.setViewRange(range);
        }
    }

    @Override
    public void detach(@NotNull Entity entity) {
        ModelEngineAPI.removeModeledEntity(entity);
    }

    @Override
    public void close() {
    }
}
