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
import org.bukkit.Location;
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
    private final ModelBridgeFactory bridgeFactory;
    private final Map<String, MobModelBridge> bridges = new ConcurrentHashMap<>();
    private final MobModelBridge defaultBridge;
    private final Map<UUID, String> attached = new ConcurrentHashMap<>();
    private final Map<UUID, MobModelBridge> attachedBridge = new ConcurrentHashMap<>();
    private final Map<UUID, String> currentMovement = new ConcurrentHashMap<>();
    private final Map<UUID, MobModelBridge.LodTier> currentLod = new ConcurrentHashMap<>();
    private final Map<String, String> engineByDefinition = new ConcurrentHashMap<>();
    private final List<AnimationRegistration> registrations = new ArrayList<>();
    private @Nullable TaskToken tickTask;
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
        this.bridgeFactory = new ModelBridgeFactory(plugin);
        this.defaultBridge = bridgeFor(null);
        this.listenerRegistration = EmakiCoreLibApi.addAnimationListener(plugin, new EnginePlaybackListener());
        if (defaultBridge.available()) {
            this.tickTask = scheduleTick();
        } else {
            this.tickTask = null;
        }
    }

    public boolean modelsEnabled() {
        return bridges.values().stream().anyMatch(MobModelBridge::available);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        if (attached.containsKey(event.getEntity().getUniqueId())) {
            detach(event.getEntity());
        }
    }

    public @Nullable String backendId() {
        return defaultBridge.available() ? defaultBridge.id() : null;
    }

    public void attachMob(LivingEntity entity, String mobId) {
        MobSpec spec = registry.get().get(mobId);
        MobModelConfig model = spec == null ? null : spec.modelConfig();
        if (model == null) {
            return;
        }
        MobModelBridge bridge = bridgeFor(model.api());
        if (!bridge.available()) {
            return;
        }
        if (!bridge.hasBlueprint(model.blueprint())) {
            messageService.warning("console.model_blueprint_missing",
                    Map.of("mob_id", mobId, "blueprint", model.blueprint()));
            return;
        }
        if (bridge.attach(entity, model.blueprint(), model.scale())) {
            attached.put(entity.getUniqueId(), mobId);
            attachedBridge.put(entity.getUniqueId(), bridge);
            ensureTicking();
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
        MobSpec spec = registry.get().get(mobId);
        MobModelConfig model = spec == null ? null : spec.modelConfig();
        MobModelBridge target = bridgeFor(model == null ? null : model.api());
        if (!target.available()) {
            return;
        }
        if (!target.hasBlueprint(blueprintId)) {
            messageService.warning("console.model_blueprint_missing",
                    Map.of("mob_id", mobId, "blueprint", blueprintId));
            return;
        }
        double scale = model == null ? 1.0 : model.scale();
        MobModelBridge previous = attachedBridge.remove(entity.getUniqueId());
        if (previous != null) {
            previous.detach(entity);
        } else {
            target.detach(entity);
        }
        if (target.attach(entity, blueprintId, scale)) {
            attached.put(entity.getUniqueId(), mobId);
            attachedBridge.put(entity.getUniqueId(), target);
            currentLod.remove(entity.getUniqueId());
            ensureTicking();
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
            MobModelBridge bridge = attachedBridge.remove(entity.getUniqueId());
            if (bridge != null) {
                bridge.detach(entity);
            }
        }
    }

    public void registerAnimationDefinitions() {
        revokeRegistrations();
        engineByDefinition.clear();
        for (MobSpec spec : registry.get().values()) {
            MobModelConfig model = spec.modelConfig();
            if (model == null) {
                continue;
            }
            MobModelBridge bridge = bridgeFor(model.api());
            if (!bridge.available()) {
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

    public synchronized void close() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (UUID entityId : List.copyOf(attached.keySet())) {
            Entity entity = plugin.getServer().getEntity(entityId);
            if (entity != null) {
                detach(entity);
            }
        }
        attached.clear();
        attachedBridge.clear();
        currentMovement.clear();
        currentLod.clear();
        revokeRegistrations();
        if (listenerRegistration != null) {
            listenerRegistration.close();
        }
        for (MobModelBridge bridge : bridges.values()) {
            bridge.close();
        }
        bridges.clear();
    }

    private void revokeRegistrations() {
        for (AnimationRegistration registration : registrations) {
            registration.close();
        }
        registrations.clear();
    }

    private MobModelBridge bridgeFor(@Nullable String apiPreference) {
        String key = apiPreference == null || apiPreference.isBlank()
                ? modelSettings().api().trim().toLowerCase(Locale.ROOT)
                : apiPreference;
        return bridges.computeIfAbsent(key, value -> bridgeFactory.create(value));
    }

    private synchronized void ensureTicking() {
        if (tickTask == null) {
            tickTask = scheduleTick();
        }
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
        applyLod(entity, mobId, settings);
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

    private void applyLod(LivingEntity entity, String mobId, ModelSettings settings) {
        MobModelBridge bridge = attachedBridge.get(entity.getUniqueId());
        if (bridge == null) {
            return;
        }
        MobSpec spec = registry.get().get(mobId);
        MobModelConfig model = spec == null ? null : spec.modelConfig();
        MobModelConfig.LodBounds bounds = model == null ? null : model.lod();
        double near = bounds == null ? settings.lodNear() : bounds.near();
        double mid = bounds == null ? settings.lodMid() : bounds.mid();
        double far = bounds == null ? settings.lodFar() : bounds.far();
        MobModelBridge.LodTier tier = tierFor(entity, near, mid, far);
        MobModelBridge.LodTier previous = currentLod.get(entity.getUniqueId());
        if (previous != tier) {
            currentLod.put(entity.getUniqueId(), tier);
            double range = switch (tier) {
                case NEAR -> settings.viewDistance();
                case MID -> Math.min(settings.viewDistance(), mid);
                case FAR -> 0.0;
            };
            bridge.applyLod(entity, tier, range);
        }
    }

    private MobModelBridge.LodTier tierFor(LivingEntity entity, double near, double mid, double far) {
        double farSquared = far * far;
        Location entityLocation = entity.getLocation();
        double nearestSquared = Double.MAX_VALUE;
        for (Player player : entity.getWorld().getPlayers()) {
            double distanceSquared = player.getLocation().distanceSquared(entityLocation);
            if (distanceSquared < nearestSquared) {
                nearestSquared = distanceSquared;
            }
        }
        if (nearestSquared > farSquared) {
            return MobModelBridge.LodTier.FAR;
        }
        if (nearestSquared <= near * near) {
            return MobModelBridge.LodTier.NEAR;
        }
        if (nearestSquared <= mid * mid) {
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
            MobModelBridge bridge = attachedBridge.getOrDefault(entity.getUniqueId(), defaultBridge);
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
                    MobModelBridge bridge = attachedBridge.getOrDefault(entity.getUniqueId(), defaultBridge);
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
