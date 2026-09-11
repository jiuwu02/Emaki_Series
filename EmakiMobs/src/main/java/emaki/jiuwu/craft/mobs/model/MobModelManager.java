package emaki.jiuwu.craft.mobs.model;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionExecutionContext;
import emaki.jiuwu.craft.corelib.api.animation.AnimationConflictPolicy;
import emaki.jiuwu.craft.corelib.api.animation.AnimationDefinition;
import emaki.jiuwu.craft.corelib.api.animation.AnimationKeyframe;
import emaki.jiuwu.craft.corelib.api.animation.AnimationListener;
import emaki.jiuwu.craft.corelib.api.animation.AnimationPlaybackHandle;
import emaki.jiuwu.craft.corelib.api.animation.AnimationPriority;
import emaki.jiuwu.craft.corelib.api.animation.AnimationRegistration;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.mobs.config.AppConfig;
import emaki.jiuwu.craft.mobs.config.ModelSettings;
import emaki.jiuwu.craft.mobs.loader.MobModelConfig;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class MobModelManager implements Listener {

    private static final int DEFAULT_ONE_SHOT_TICKS = 20;

    private final Plugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final Supplier<Map<String, MobSpec>> registry;
    private final Supplier<AppConfig> configSupplier;
    private final MessageService messageService;
    private final MobModelBridge bridge;
    private final Map<UUID, String> attached = new ConcurrentHashMap<>();
    private final Map<UUID, String> currentMovement = new ConcurrentHashMap<>();
    private final Map<UUID, MobModelBridge.LodTier> currentLod = new ConcurrentHashMap<>();
    private final Map<String, String> engineByDefinition = new ConcurrentHashMap<>();
    private final List<AnimationRegistration> registrations = new ArrayList<>();
    private final @Nullable TaskToken tickTask;
    private final AnimationRegistration listenerRegistration;

    public MobModelManager(Plugin plugin,
            ExecutionDispatcher executionDispatcher,
            Supplier<Map<String, MobSpec>> registry,
            Supplier<AppConfig> configSupplier,
            MessageService messageService) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        this.registry = registry;
        this.configSupplier = configSupplier;
        this.messageService = messageService;
        this.bridge = new ModelBridgeFactory(plugin).create(modelSettings().api());
        this.listenerRegistration = EmakiCoreLibApi.addAnimationListener(plugin, new EnginePlaybackListener());
        if (bridge.available()) {
            this.tickTask = scheduleTick();
        } else {
            this.tickTask = null;
        }
    }

    public boolean modelsEnabled() {
        return bridge.available();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        if (attached.containsKey(event.getEntity().getUniqueId())) {
            detach(event.getEntity());
        }
    }

    public @Nullable String backendId() {
        return bridge.available() ? bridge.id() : null;
    }

    public void attachMob(LivingEntity entity, String mobId) {
        if (!bridge.available()) {
            return;
        }
        MobSpec spec = registry.get().get(mobId);
        MobModelConfig model = spec == null ? null : spec.modelConfig();
        if (model == null) {
            return;
        }
        if (!bridge.hasBlueprint(model.blueprint())) {
            messageService.warning("console.model_blueprint_missing",
                    Map.of("mob_id", mobId, "blueprint", model.blueprint()));
            return;
        }
        if (bridge.attach(entity, model.blueprint(), model.scale())) {
            attached.put(entity.getUniqueId(), mobId);
            playStandard(entity, mobId, StandardAnimation.IDLE);
        }
    }

    public void playStandard(LivingEntity entity, String mobId, StandardAnimation animation) {
        if (!attached.containsKey(entity.getUniqueId())) {
            return;
        }
        String definitionId = definitionId(mobId, animation.configKey());
        CoreActionExecutionContext context = CoreActionExecutionContext.builder()
                .caster(entity)
                .phase("animation")
                .build();
        EmakiCoreLibApi.playAnimationAsync(plugin, entity, definitionId, context);
    }

    public void playNamed(LivingEntity entity, String mobId, String animationKey) {
        if (!attached.containsKey(entity.getUniqueId())) {
            return;
        }
        String definitionId = definitionId(mobId, animationKey.toLowerCase(Locale.ROOT));
        CoreActionExecutionContext context = CoreActionExecutionContext.builder()
                .caster(entity)
                .phase("animation")
                .build();
        EmakiCoreLibApi.playAnimationAsync(plugin, entity, definitionId, context);
    }

    public void stopNamed(LivingEntity entity, String mobId, String animationKey) {
        String definitionId = definitionId(mobId, animationKey.toLowerCase(Locale.ROOT));
        EmakiCoreLibApi.stopAnimation(plugin, entity, definitionId);
    }

    public void setModel(LivingEntity entity, String mobId, String blueprintId) {
        if (!bridge.available()) {
            return;
        }
        if (!bridge.hasBlueprint(blueprintId)) {
            messageService.warning("console.model_blueprint_missing",
                    Map.of("mob_id", mobId, "blueprint", blueprintId));
            return;
        }
        MobSpec spec = registry.get().get(mobId);
        double scale = spec == null || spec.modelConfig() == null ? 1.0 : spec.modelConfig().scale();
        bridge.detach(entity);
        if (bridge.attach(entity, blueprintId, scale)) {
            attached.put(entity.getUniqueId(), mobId);
            playStandard(entity, mobId, StandardAnimation.IDLE);
        }
    }

    public void onDeath(LivingEntity entity, String mobId) {
        if (!attached.containsKey(entity.getUniqueId())) {
            return;
        }
        playStandard(entity, mobId, StandardAnimation.DEATH);
        int delay = Math.max(0, modelSettings().deathDetachDelayTicks());
        executionDispatcher.runEntityLater(plugin, entity, () -> detach(entity), delay);
    }

    public void detach(Entity entity) {
        if (attached.remove(entity.getUniqueId()) != null) {
            currentMovement.remove(entity.getUniqueId());
            currentLod.remove(entity.getUniqueId());
            bridge.detach(entity);
        }
    }

    public void registerAnimationDefinitions() {
        revokeRegistrations();
        engineByDefinition.clear();
        if (!bridge.available()) {
            return;
        }
        for (MobSpec spec : registry.get().values()) {
            MobModelConfig model = spec.modelConfig();
            if (model == null) {
                continue;
            }
            for (StandardAnimation animation : StandardAnimation.values()) {
                registerDefinition(spec, model, animation.configKey(), animation.defaultAnimation(),
                        animation.priority(), animation.loops());
            }
            for (String custom : model.keyframes().keySet()) {
                if (isStandardKey(custom)) {
                    continue;
                }
                registerDefinition(spec, model, custom, custom, AnimationPriority.UTILITY, false);
            }
        }
    }

    private void registerDefinition(MobSpec spec, MobModelConfig model, String animationKey, String defaultEngine,
            AnimationPriority priority, boolean defaultLoop) {
        MobModelConfig.KeyframeTimeline timeline = model.keyframes().get(animationKey);
        String engine = model.animations().getOrDefault(animationKey, defaultEngine);
        int duration = timeline == null ? DEFAULT_ONE_SHOT_TICKS : timeline.durationTicks();
        boolean loop = timeline == null ? defaultLoop : timeline.loop();
        AnimationPriority resolvedPriority = timeline == null
                ? priority
                : AnimationPriority.parse(timeline.priority());
        AnimationConflictPolicy conflict = timeline == null
                ? AnimationConflictPolicy.REPLACE
                : AnimationConflictPolicy.parse(timeline.conflict());
        List<AnimationKeyframe> frames = timeline == null ? List.of() : timeline.frames();
        AnimationDefinition definition = AnimationDefinition.of(definitionId(spec.id(), animationKey), engine,
                duration, loop, resolvedPriority, conflict, frames);
        engineByDefinition.put(definition.id(), engine);
        AnimationRegistration registration = EmakiCoreLibApi.registerAnimation(plugin, definition);
        if (registration.successful()) {
            registrations.add(registration);
        }
    }

    public void reload() {
        registerAnimationDefinitions();
    }

    public void close() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        for (UUID entityId : List.copyOf(attached.keySet())) {
            Entity entity = plugin.getServer().getEntity(entityId);
            if (entity != null) {
                detach(entity);
            }
        }
        attached.clear();
        currentMovement.clear();
        currentLod.clear();
        revokeRegistrations();
        if (listenerRegistration != null) {
            listenerRegistration.close();
        }
        bridge.close();
    }

    private void revokeRegistrations() {
        for (AnimationRegistration registration : registrations) {
            registration.close();
        }
        registrations.clear();
    }

    private @Nullable TaskToken scheduleTick() {
        int interval = Math.max(1, modelSettings().movementCheckIntervalTicks());
        return executionDispatcher.runGlobalTimer(plugin, this::tickAll, interval, interval);
    }

    private void tickAll() {
        if (attached.isEmpty()) {
            return;
        }
        ModelSettings settings = modelSettings();
        for (Map.Entry<UUID, String> entry : attached.entrySet()) {
            Entity raw = plugin.getServer().getEntity(entry.getKey());
            if (!(raw instanceof LivingEntity entity) || !entity.isValid() || entity.isDead()) {
                continue;
            }
            executionDispatcher.runEntity(plugin, entity, () -> tickEntity(entity, entry.getValue(), settings));
        }
    }

    private void tickEntity(LivingEntity entity, String mobId, ModelSettings settings) {
        applyMovement(entity, mobId, settings);
        applyLod(entity, settings);
    }

    private void applyMovement(LivingEntity entity, String mobId, ModelSettings settings) {
        double horizontal = Math.sqrt(entity.getVelocity().getX() * entity.getVelocity().getX()
                + entity.getVelocity().getZ() * entity.getVelocity().getZ());
        StandardAnimation movement;
        if (horizontal >= settings.runSpeedThreshold()) {
            movement = StandardAnimation.RUN;
        } else if (horizontal >= settings.walkSpeedThreshold()) {
            movement = StandardAnimation.WALK;
        } else {
            movement = StandardAnimation.IDLE;
        }
        String key = movement.configKey();
        if (!key.equals(currentMovement.get(entity.getUniqueId()))) {
            currentMovement.put(entity.getUniqueId(), key);
            playStandard(entity, mobId, movement);
        }
    }

    private void applyLod(LivingEntity entity, ModelSettings settings) {
        MobModelBridge.LodTier tier = tierFor(entity, settings);
        MobModelBridge.LodTier previous = currentLod.get(entity.getUniqueId());
        if (previous != tier) {
            currentLod.put(entity.getUniqueId(), tier);
            bridge.applyLod(entity, tier, settings.viewDistance());
        }
    }

    private MobModelBridge.LodTier tierFor(LivingEntity entity, ModelSettings settings) {
        double radius = settings.lodFar() + 8.0;
        double nearestSquared = Double.MAX_VALUE;
        double farSquared = settings.lodFar() * settings.lodFar();
        for (Entity nearby : entity.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof Player)) {
                continue;
            }
            double distanceSquared = nearby.getLocation().distanceSquared(entity.getLocation());
            if (distanceSquared < nearestSquared) {
                nearestSquared = distanceSquared;
            }
        }
        if (nearestSquared > farSquared) {
            return MobModelBridge.LodTier.FAR;
        }
        if (nearestSquared <= settings.lodNear() * settings.lodNear()) {
            return MobModelBridge.LodTier.NEAR;
        }
        if (nearestSquared <= settings.lodMid() * settings.lodMid()) {
            return MobModelBridge.LodTier.MID;
        }
        return MobModelBridge.LodTier.FAR;
    }

    private ModelSettings modelSettings() {
        AppConfig config = configSupplier.get();
        return config == null ? ModelSettings.defaults() : config.model();
    }

    private static String definitionId(String mobId, String animationKey) {
        return "mob:" + mobId.toLowerCase(Locale.ROOT) + ":" + animationKey.toLowerCase(Locale.ROOT);
    }

    private static boolean isStandardKey(String key) {
        for (StandardAnimation animation : StandardAnimation.values()) {
            if (animation.configKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private final class EnginePlaybackListener implements AnimationListener {

        @Override
        public void onPlay(Entity entity, AnimationDefinition definition, UUID playbackId) {
            if (!bridge.available()) {
                return;
            }
            String engine = definition.engineAnimation();
            if (engine.isBlank()) {
                return;
            }
            ModelSettings settings = modelSettings();
            int fade = settings.fadeTicks(standardKeyOf(definition.id()));
            bridge.playAnimation(entity, engine, 1.0, fade, definition.loop());
        }

        @Override
        public void onStop(@Nullable Entity entity, String definitionId, UUID playbackId,
                AnimationPlaybackHandle.State state) {
            if (entity == null) {
                return;
            }
            if (state == AnimationPlaybackHandle.State.CANCELLED) {
                String engine = engineByDefinition.get(definitionId);
                if (engine != null && !engine.isBlank()) {
                    bridge.stopAnimation(entity, engine);
                }
                return;
            }
            if (state != AnimationPlaybackHandle.State.FINISHED) {
                return;
            }
            String mobId = attached.get(entity.getUniqueId());
            if (mobId == null || !(entity instanceof LivingEntity living) || living.isDead()) {
                return;
            }
            if (isTransient(definitionId)) {
                playStandard(living, mobId, StandardAnimation.IDLE);
            }
        }
    }

    private static String standardKeyOf(String definitionId) {
        int last = definitionId.lastIndexOf(':');
        return last < 0 ? definitionId : definitionId.substring(last + 1);
    }

    private static boolean isTransient(String definitionId) {
        String key = standardKeyOf(definitionId);
        return key.equals(StandardAnimation.ATTACK.configKey())
                || key.equals(StandardAnimation.HURT.configKey())
                || key.equals(StandardAnimation.SKILL.configKey())
                || key.equals(StandardAnimation.INTERACT.configKey());
    }
}
