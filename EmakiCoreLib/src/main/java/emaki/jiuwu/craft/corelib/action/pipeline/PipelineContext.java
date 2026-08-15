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
    private final Map<String, String> variables;
    private final Map<CoreActionKey<?>, Object> data;
    private final int currentTargetIndex;
    private final PlaceholderBridge placeholders;

    private PipelineContext(Plugin sourcePlugin,
            CoreActionSubject caster,
            List<CoreActionSubject> targets,
            Location origin,
            String phase,
            boolean silent,
            Map<String, String> variables,
            Map<CoreActionKey<?>, Object> data,
            int currentTargetIndex,
            PlaceholderBridge placeholders) {
        this.sourcePlugin = sourcePlugin;
        this.caster = caster == null ? CoreActionSubject.absent() : caster;
        this.targets = targets == null ? List.of() : List.copyOf(targets);
        this.origin = origin;
        this.phase = Texts.isBlank(phase) ? "default" : Texts.trim(phase);
        this.silent = silent;
        this.variables = variables == null ? Map.of() : Map.copyOf(variables);
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
        CoreActionSubject resolvedCaster = caster == null ? CoreActionSubject.absent() : caster;
        Location resolvedOrigin = origin != null ? origin.clone() : resolvedCaster.location();
        return new PipelineContext(sourcePlugin, resolvedCaster, List.of(), resolvedOrigin,
                phase, silent, Map.of(), Map.of(), 0, placeholders);
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
        throw new IllegalStateException("pipeline context has no origin: caster="
                + caster.getClass().getSimpleName() + ", phase=" + phase);
    }

    public boolean hasOrigin() {
        return origin != null || caster.location() != null;
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
            throw new IllegalArgumentException("key must not be null");
        }
        T value = key.cast(data.get(key));
        if (value != null) {
            return value;
        }
        throw new IllegalStateException("missing required context key '" + key.name()
                + "' of type " + key.type().getSimpleName()
                + "; context holds " + presentKeys());
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
        return Optional.ofNullable(variables.get(Texts.lower(name)));
    }

    public @NotNull Map<String, String> variables() {
        return variables;
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
                variables, data, 0, placeholders);
    }

    public @NotNull PipelineContext withTargetIndex(int index) {
        return new PipelineContext(sourcePlugin, caster, targets, origin, phase, silent,
                variables, data, index, placeholders);
    }

    public @NotNull PipelineContext withVariable(@Nullable String name, @Nullable Object value) {
        if (Texts.isBlank(name)) {
            return this;
        }
        Map<String, String> copy = new LinkedHashMap<>(variables);
        copy.put(Texts.lower(name), Texts.toStringSafe(value));
        return new PipelineContext(sourcePlugin, caster, targets, origin, phase, silent,
                copy, data, currentTargetIndex, placeholders);
    }

    public @NotNull PipelineContext withVariables(@Nullable Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        Map<String, String> copy = new LinkedHashMap<>(variables);
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (Texts.isBlank(entry.getKey())) {
                continue;
            }
            copy.put(Texts.lower(entry.getKey()), Texts.toStringSafe(entry.getValue()));
        }
        return new PipelineContext(sourcePlugin, caster, targets, origin, phase, silent,
                copy, data, currentTargetIndex, placeholders);
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
                variables, copy, currentTargetIndex, placeholders);
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
                variables, copy, currentTargetIndex, placeholders);
    }

    public @NotNull PipelineContext withOrigin(@Nullable Location newOrigin) {
        return new PipelineContext(sourcePlugin, caster, targets, origin == null && newOrigin == null
                ? null : (newOrigin == null ? origin : newOrigin.clone()),
                phase, silent, variables, data, currentTargetIndex, placeholders);
    }

    public @NotNull PipelineContext withPhase(@Nullable String newPhase) {
        return new PipelineContext(sourcePlugin, caster, targets, origin, newPhase, silent,
                variables, data, currentTargetIndex, placeholders);
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
                scoped, data, currentTargetIndex, placeholders);
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
}
