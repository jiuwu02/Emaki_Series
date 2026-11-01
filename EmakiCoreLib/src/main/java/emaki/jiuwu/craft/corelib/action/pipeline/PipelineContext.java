package emaki.jiuwu.craft.corelib.action.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class PipelineContext implements CoreStageContext {

    private final Plugin sourcePlugin;
    private final CoreActionSubject caster;
    private final List<CoreActionSubject> targets;
    private final Location origin;
    private final String phase;
    private final boolean silent;
    private final VariableScope variableScope;
    private final Map<CoreActionKey<?>, Object> data;
    private final int currentTargetIndex;
    private final PlaceholderBridge placeholders;

    private PipelineContext(Plugin sourcePlugin,
            CoreActionSubject caster,
            List<CoreActionSubject> targets,
            Location origin,
            String phase,
            boolean silent,
            VariableScope variableScope,
            Map<CoreActionKey<?>, Object> data,
            int currentTargetIndex,
            PlaceholderBridge placeholders) {
        this.sourcePlugin = sourcePlugin;
        this.caster = caster == null ? CoreActionSubject.absent() : caster;
        this.targets = targets == null ? List.of() : List.copyOf(targets);
        this.origin = origin;
        this.phase = Texts.isBlank(phase) ? "default" : Texts.trim(phase);
        this.silent = silent;
        this.variableScope = variableScope == null ? VariableScope.EMPTY : variableScope;
        this.data = data == null ? Map.of() : Map.copyOf(data);
        this.currentTargetIndex = Math.max(0, currentTargetIndex);
        this.placeholders = placeholders == null ? PlaceholderBridge.noop() : placeholders;
    }

    public static @NotNull PipelineContext root(@Nullable Plugin sourcePlugin,
            @Nullable CoreActionSubject caster,
            @Nullable Location origin,
            @Nullable String phase,
            boolean silent,
            @Nullable PlaceholderBridge placeholders) {
        return root(sourcePlugin, caster, List.of(), origin, phase, silent, Map.of(), Map.of(), placeholders);
    }

    public static @NotNull PipelineContext root(@Nullable Plugin sourcePlugin,
            @Nullable CoreActionSubject caster,
            @Nullable List<CoreActionSubject> targets,
            @Nullable Location origin,
            @Nullable String phase,
            boolean silent,
            @Nullable Map<String, String> variables,
            @Nullable Map<CoreActionKey<?>, Object> data,
            @Nullable PlaceholderBridge placeholders) {
        CoreActionSubject resolvedCaster = caster == null ? CoreActionSubject.absent() : caster;
        Location resolvedOrigin = origin == null ? null : origin.clone();
        return new PipelineContext(sourcePlugin, resolvedCaster, targets, resolvedOrigin,
                phase, silent, VariableScope.base(variables), data, 0, placeholders);
    }

    @Override
    public @Nullable Plugin sourcePlugin() {
        return sourcePlugin;
    }

    @Override
    public @NotNull CoreActionSubject caster() {
        return caster;
    }

    @Override
    public @NotNull List<CoreActionSubject> targets() {
        return targets;
    }

    @Override
    public @NotNull CoreActionSubject currentTarget() {
        if (targets.isEmpty() || currentTargetIndex >= targets.size()) {
            return CoreActionSubject.absent();
        }
        return targets.get(currentTargetIndex);
    }

    @Override
    public int currentTargetIndex() {
        return currentTargetIndex;
    }

    @Override
    public @NotNull Location origin() {
        if (origin != null) {
            return origin;
        }
        Location casterLocation = caster.location();
        if (casterLocation != null) {
            return casterLocation;
        }
        throw new IllegalStateException("管道上下文没有原点: caster="
                + caster.getClass().getSimpleName() + ", phase=" + phase);
    }

    public boolean hasOrigin() {
        return origin != null;
    }

    public @Nullable Location explicitOrigin() {
        return origin == null ? null : origin.clone();
    }

    @Override
    public @NotNull String phase() {
        return phase;
    }

    @Override
    public boolean silent() {
        return silent;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull <T> Optional<T> get(@NotNull CoreActionKey<T> key) {
        if (key == null) {
            return Optional.empty();
        }
        Object value = data.get(key);
        return Optional.ofNullable((T) key.cast(value));
    }

    @Override
    public @NotNull <T> T require(@NotNull CoreActionKey<T> key) {
        if (key == null) {
            throw new IllegalArgumentException("key 不能为 null");
        }
        T value = key.cast(data.get(key));
        if (value != null) {
            return value;
        }
        throw new IllegalStateException("缺少必需的上下文键 '" + key.name()
                + "'，类型 " + key.type().getSimpleName()
                + "；上下文包含 " + presentKeys());
    }

    @Override
    public @NotNull List<CoreActionKey<?>> presentKeys() {
        return List.copyOf(data.keySet());
    }

    @Override
    public @NotNull Optional<String> variable(@Nullable String name) {
        if (Texts.isBlank(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(variableScope.get(Texts.lower(name)));
    }

    public @NotNull Map<String, String> variables() {
        return variableScope.resolved();
    }

    @Override
    public @NotNull String render(@Nullable String template) {
        return placeholders.render(this, template);
    }

    public @NotNull PlaceholderBridge placeholders() {
        return placeholders;
    }

    public @NotNull PipelineContext withTargets(@Nullable List<CoreActionSubject> newTargets) {
        return new PipelineContext(sourcePlugin, caster, newTargets, origin, phase, silent,
                variableScope, data, 0, placeholders);
    }

    public @NotNull PipelineContext withTargetIndex(int index) {
        return new PipelineContext(sourcePlugin, caster, targets, origin, phase, silent,
                variableScope, data, index, placeholders);
    }

    public @NotNull PipelineContext withVariable(@Nullable String name, @Nullable Object value) {
        if (Texts.isBlank(name)) {
            return this;
        }
        Map<String, String> overlay = new LinkedHashMap<>(1);
        overlay.put(Texts.lower(name), Texts.toStringSafe(value));
        return new PipelineContext(sourcePlugin, caster, targets, origin, phase, silent,
                variableScope.overlay(overlay), data, currentTargetIndex, placeholders);
    }

    public @NotNull PipelineContext withVariables(@Nullable Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        Map<String, String> overlay = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (Texts.isBlank(entry.getKey())) {
                continue;
            }
            overlay.put(Texts.lower(entry.getKey()), Texts.toStringSafe(entry.getValue()));
        }
        return new PipelineContext(sourcePlugin, caster, targets, origin, phase, silent,
                variableScope.overlay(overlay), data, currentTargetIndex, placeholders);
    }

    public @NotNull <T> PipelineContext with(@NotNull CoreActionKey<T> key, @Nullable T value) {
        if (key == null) {
            return this;
        }
        Map<CoreActionKey<?>, Object> copy = new LinkedHashMap<>(data);
        if (value == null) {
            copy.remove(key);
        } else {
            copy.put(key, value);
        }
        return new PipelineContext(sourcePlugin, caster, targets, origin, phase, silent,
                variableScope, copy, currentTargetIndex, placeholders);
    }

    public @NotNull PipelineContext withData(@Nullable Map<CoreActionKey<?>, Object> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        Map<CoreActionKey<?>, Object> copy = new LinkedHashMap<>(data);
        for (Map.Entry<CoreActionKey<?>, Object> entry : values.entrySet()) {
            CoreActionKey<?> key = entry.getKey();
            if (key == null) {
                continue;
            }
            if (entry.getValue() == null) {
                copy.remove(key);
            } else if (key.type().isInstance(entry.getValue())) {
                copy.put(key, entry.getValue());
            }
        }
        return new PipelineContext(sourcePlugin, caster, targets, origin, phase, silent,
                variableScope, copy, currentTargetIndex, placeholders);
    }

    public @NotNull PipelineContext withOrigin(@Nullable Location newOrigin) {
        return new PipelineContext(sourcePlugin, caster, targets, origin == null && newOrigin == null
                ? null : (newOrigin == null ? origin : newOrigin.clone()),
                phase, silent, variableScope, data, currentTargetIndex, placeholders);
    }

    public @NotNull PipelineContext withPhase(@Nullable String newPhase) {
        return new PipelineContext(sourcePlugin, caster, targets, origin, newPhase, silent,
                variableScope, data, currentTargetIndex, placeholders);
    }

    public @NotNull PipelineContext isolated(@Nullable Map<String, String> parameters) {
        Map<String, String> scoped = new LinkedHashMap<>();
        if (parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                if (Texts.isBlank(entry.getKey())) {
                    continue;
                }
                scoped.put(Texts.lower(entry.getKey()), Texts.toStringSafe(entry.getValue()));
            }
        }
        return new PipelineContext(sourcePlugin, caster, targets, origin, phase, silent,
                VariableScope.base(scoped), data, currentTargetIndex, placeholders);
    }

    public @NotNull PipelineContext revalidated() {
        if (targets.isEmpty()) {
            return this;
        }
        List<CoreActionSubject> alive = new ArrayList<>(targets.size());
        for (CoreActionSubject subject : targets) {
            if (subject.valid()) {
                alive.add(subject);
            }
        }
        return alive.size() == targets.size() ? this : withTargets(alive);
    }

    private static final class VariableScope {

        private static final VariableScope EMPTY = new VariableScope(null, Map.of());

        private final VariableScope parent;
        private final Map<String, String> entries;
        private volatile Map<String, String> resolved;

        private VariableScope(VariableScope parent, Map<String, String> entries) {
            this.parent = parent;
            this.entries = entries;
        }

        private static VariableScope base(Map<String, String> variables) {
            return variables == null || variables.isEmpty()
                    ? EMPTY
                    : new VariableScope(null, Map.copyOf(variables));
        }

        private VariableScope overlay(Map<String, String> values) {
            return values == null || values.isEmpty()
                    ? this
                    : new VariableScope(this, Map.copyOf(values));
        }

        private String get(String key) {
            VariableScope scope = this;
            while (scope != null) {
                String value = scope.entries.get(key);
                if (value != null) {
                    return value;
                }
                scope = scope.parent;
            }
            return null;
        }

        private Map<String, String> resolved() {
            Map<String, String> cached = resolved;
            if (cached != null) {
                return cached;
            }
            Map<String, String> computed;
            if (parent == null) {
                computed = entries;
            } else if (entries.isEmpty()) {
                computed = parent.resolved();
            } else {
                Map<String, String> parentValues = parent.resolved();
                Map<String, String> merged = new LinkedHashMap<>(parentValues.size() + entries.size());
                merged.putAll(parentValues);
                merged.putAll(entries);
                computed = Map.copyOf(merged);
            }
            resolved = computed;
            return computed;
        }
    }
}
