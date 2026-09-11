package emaki.jiuwu.craft.corelib.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionEngine;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.action.pipeline.RegistryPlaceholderBridge;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionExecutionContext;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.api.animation.AnimationConflictPolicy;
import emaki.jiuwu.craft.corelib.api.animation.AnimationDefinition;
import emaki.jiuwu.craft.corelib.api.animation.AnimationKeyframe;
import emaki.jiuwu.craft.corelib.api.animation.AnimationListener;
import emaki.jiuwu.craft.corelib.api.animation.AnimationPlaybackHandle;
import emaki.jiuwu.craft.corelib.api.animation.AnimationPriority;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class AnimationPlaybackService {

    private static final PhaseContract KEYFRAME_PHASE = PhaseContract.permissive("animation");

    private final EmakiCoreLibPlugin plugin;
    private final AnimationRegistry registry;
    private final Map<UUID, Map<String, ActivePlayback>> byEntity = new ConcurrentHashMap<>();
    private final Map<String, CompiledPipeline> compiledFrames = new ConcurrentHashMap<>();
    private final AtomicInteger playbackCounter = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();

    public AnimationPlaybackService(@NotNull EmakiCoreLibPlugin plugin, @NotNull AnimationRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public @NotNull CompletableFuture<EmakiResult<AnimationPlaybackHandle>> play(@Nullable Plugin owner,
            @Nullable Entity entity,
            @Nullable String definitionId,
            @Nullable CoreActionExecutionContext context) {
        if (closed.get()) {
            return completed(EmakiResult.unavailable());
        }
        if (owner == null) {
            return completed(EmakiResult.invalidInput("animation.owner_required"));
        }
        if (!owner.isEnabled()) {
            return completed(EmakiResult.failure(FailureKind.REJECTED, "animation.owner_disabled"));
        }
        if (entity == null) {
            return completed(EmakiResult.invalidInput("animation.entity_required"));
        }
        AnimationDefinition definition = registry.find(owner, definitionId);
        if (definition == null) {
            return completed(EmakiResult.notFound("animation.definition_unknown"));
        }
        CoreActionExecutionContext resolved = context == null
                ? CoreActionExecutionContext.builder().build()
                : context;
        CompletableFuture<EmakiResult<AnimationPlaybackHandle>> future = new CompletableFuture<>();
        ExecutionDispatcher dispatcher = plugin.executionDispatcher();
        if (dispatcher == null) {
            return completed(EmakiResult.unavailable());
        }
        dispatcher.runEntity(owner, entity, () -> {
            if (!entity.isValid()) {
                future.complete(EmakiResult.rejected("animation.entity_invalid"));
                return;
            }
            future.complete(startOnEntityThread(owner, entity, definition, resolved));
        }, () -> future.complete(EmakiResult.rejected("animation.entity_retired")));
        return future;
    }

    public boolean stop(@Nullable Plugin owner, @Nullable Entity entity, @Nullable String definitionId) {
        if (owner == null || entity == null) {
            return false;
        }
        Map<String, ActivePlayback> slots = byEntity.get(entity.getUniqueId());
        if (slots == null || slots.isEmpty()) {
            return false;
        }
        String key = definitionId == null || definitionId.isBlank()
                ? null
                : definitionId.trim().toLowerCase(Locale.ROOT);
        List<ActivePlayback> targets = new ArrayList<>();
        for (ActivePlayback playback : slots.values()) {
            if (playback.owner != owner) {
                continue;
            }
            if (key == null || key.equals(playback.definition.id())) {
                targets.add(playback);
            }
        }
        for (ActivePlayback playback : targets) {
            playback.cancel();
        }
        return !targets.isEmpty();
    }

    public void revokeOwner(@Nullable Plugin owner) {
        if (owner == null) {
            return;
        }
        for (Map<String, ActivePlayback> slots : byEntity.values()) {
            for (ActivePlayback playback : List.copyOf(slots.values())) {
                if (playback.owner == owner) {
                    playback.cancel();
                }
            }
        }
    }

    public void invalidateCompiledFrames() {
        compiledFrames.clear();
    }

    public void stopAll() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (Map<String, ActivePlayback> slots : byEntity.values()) {
            for (ActivePlayback playback : List.copyOf(slots.values())) {
                playback.cancel();
            }
        }
        byEntity.clear();
        compiledFrames.clear();
    }

    private EmakiResult<AnimationPlaybackHandle> startOnEntityThread(Plugin owner,
            Entity entity,
            AnimationDefinition definition,
            CoreActionExecutionContext context) {
        Map<String, ActivePlayback> slots =
                byEntity.computeIfAbsent(entity.getUniqueId(), key -> new ConcurrentHashMap<>());
        AnimationPriority incoming = definition.priority();
        for (ActivePlayback active : List.copyOf(slots.values())) {
            int comparison = Integer.compare(incoming.ordinal(), active.definition.priority().ordinal());
            if (comparison < 0) {
                return EmakiResult.rejected("animation.conflict_lower_priority");
            }
            if (comparison == 0 && definition.conflictPolicy() == AnimationConflictPolicy.REJECT) {
                return EmakiResult.rejected("animation.conflict_rejected");
            }
            active.cancel();
        }
        ActivePlayback playback = new ActivePlayback(owner, entity, definition, context, slots);
        slots.put(definition.id(), playback);
        notifyListeners(listener -> listener.onPlay(entity, definition, playback.playbackId()));
        playback.advance(0);
        return EmakiResult.success(playback);
    }

    private void notifyListeners(Consumer<AnimationListener> action) {
        for (AnimationListener listener : registry.listeners()) {
            try {
                action.accept(listener);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Animation listener threw an exception", exception);
            }
        }
    }

    private static <T> CompletableFuture<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private final class ActivePlayback implements AnimationPlaybackHandle {

        private final UUID playbackId = new UUID(0L, playbackCounter.incrementAndGet());
        private final Plugin owner;
        private final Entity entity;
        private final AnimationDefinition definition;
        private final CoreActionExecutionContext context;
        private final Map<String, ActivePlayback> slots;
        private final AtomicReference<State> state = new AtomicReference<>(State.PLAYING);
        private final AtomicReference<TaskToken> token = new AtomicReference<>();
        private final AtomicBoolean notifiedStop = new AtomicBoolean();
        private int elapsed;
        private int frameIndex;

        private ActivePlayback(Plugin owner,
                Entity entity,
                AnimationDefinition definition,
                CoreActionExecutionContext context,
                Map<String, ActivePlayback> slots) {
            this.owner = owner;
            this.entity = entity;
            this.definition = definition;
            this.context = context;
            this.slots = slots;
        }

        @Override
        public @NotNull UUID playbackId() {
            return playbackId;
        }

        @Override
        public @NotNull String definitionId() {
            return definition.id();
        }

        @Override
        public @NotNull UUID entityId() {
            return entity.getUniqueId();
        }

        @Override
        public @NotNull State state() {
            return state.get();
        }

        @Override
        public void cancel() {
            terminate(State.CANCELLED);
        }

        void advance(int fromElapsed) {
            if (state.get() != State.PLAYING) {
                return;
            }
            if (!entity.isValid()) {
                terminate(State.CANCELLED);
                return;
            }
            elapsed = fromElapsed;
            List<AnimationKeyframe> frames = definition.keyframes();
            while (frameIndex < frames.size() && frames.get(frameIndex).tick() <= elapsed) {
                AnimationKeyframe frame = frames.get(frameIndex);
                notifyListeners(listener -> listener.onKeyframe(entity, definition.id(), playbackId, frame));
                runFrame(frame);
                frameIndex++;
            }
            int nextTick;
            boolean wrap;
            if (frameIndex < frames.size()) {
                nextTick = frames.get(frameIndex).tick();
                wrap = false;
            } else if (definition.loop()) {
                nextTick = definition.durationTicks();
                wrap = true;
            } else {
                terminate(State.FINISHED);
                return;
            }
            long delay = Math.max(1L, nextTick - elapsed);
            int targetElapsed = nextTick;
            ExecutionDispatcher dispatcher = plugin.executionDispatcher();
            if (dispatcher == null) {
                terminate(State.CANCELLED);
                return;
            }
            token.set(dispatcher.runEntityLater(owner, entity, () -> {
                if (wrap) {
                    elapsed = 0;
                    frameIndex = 0;
                    advance(0);
                } else {
                    advance(targetElapsed);
                }
            }, () -> terminate(State.CANCELLED), delay));
        }

        private void runFrame(AnimationKeyframe frame) {
            if (!frame.executable()) {
                return;
            }
            try {
                ActionEngine engine = plugin.actionEngine();
                if (engine == null) {
                    return;
                }
                CompiledPipeline pipeline = compiledFrames.computeIfAbsent(frame.actionLine(), line -> {
                    ActionEngine.Result result = engine.compile(line, KEYFRAME_PHASE);
                    return result.successful() ? result.pipeline() : null;
                });
                if (pipeline == null) {
                    plugin.getLogger().warning("Animation keyframe line did not compile: " + frame.actionLine());
                    compiledFrames.remove(frame.actionLine());
                    return;
                }
                engine.run(owner, pipeline, buildContext());
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Animation keyframe line failed: " + frame.actionLine(), exception);
            }
        }

        private PipelineContext buildContext() {
            CoreActionSubject caster = context.caster();
            if (caster instanceof CoreActionSubject.Absent) {
                caster = CoreActionSubject.of(entity);
            }
            return PipelineContext.root(owner, caster, context.targets(),
                    context.origin().orElse(null),
                    context.phase().isBlank() ? "animation" : context.phase(),
                    context.silent(),
                    context.variables(),
                    context.data(),
                    new RegistryPlaceholderBridge(plugin::placeholderRegistry));
        }

        private void terminate(State terminal) {
            if (!state.compareAndSet(State.PLAYING, terminal)) {
                return;
            }
            TaskToken scheduled = token.getAndSet(null);
            if (scheduled != null) {
                scheduled.cancel();
            }
            slots.remove(definition.id(), this);
            if (slots.isEmpty()) {
                byEntity.remove(entity.getUniqueId(), slots);
            }
            if (notifiedStop.compareAndSet(false, true)) {
                Entity target = entity.isValid() ? entity : null;
                notifyListeners(listener -> listener.onStop(target, definition.id(), playbackId, terminal));
            }
        }
    }
}
