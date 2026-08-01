package emaki.jiuwu.craft.corelib.action.v2;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.v2.compile.ValueParsers;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Typed argument view over one stage's resolved values.
 *
 * <p>Reuses {@link ValueParsers} for every scalar shape so v2 and v1 parse identical text the same
 * way. Declared defaults are applied here, so stages never repeat null handling.</p>
 */
public final class ResolvedArguments implements CoreResolvedArguments {

    private static final ResolvedArguments EMPTY = new ResolvedArguments(Map.of(), Map.of());

    private final Map<String, String> values;
    private final Map<String, String> defaults;

    private ResolvedArguments(Map<String, String> values, Map<String, String> defaults) {
        this.values = values;
        this.defaults = defaults;
    }

    /** {@return an argument view with no values} */
    public static @NotNull ResolvedArguments empty() {
        return EMPTY;
    }

    /**
     * Builds an argument view.
     *
     * @param values resolved values, keyed by argument name in any case
     * @param declared the stage's declared parameters, used for defaults
     * @return the view
     */
    public static @NotNull ResolvedArguments of(@Nullable Map<String, String> values,
            @Nullable Iterable<CoreStageParameter> declared) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (Texts.isBlank(entry.getKey())) {
                    continue;
                }
                normalized.put(Texts.lower(entry.getKey()), Texts.toStringSafe(entry.getValue()));
            }
        }
        Map<String, String> declaredDefaults = new LinkedHashMap<>();
        if (declared != null) {
            for (CoreStageParameter parameter : declared) {
                if (parameter == null || Texts.isBlank(parameter.name())) {
                    continue;
                }
                declaredDefaults.put(Texts.lower(parameter.name()), parameter.defaultValue());
            }
        }
        return new ResolvedArguments(Map.copyOf(normalized), Map.copyOf(declaredDefaults));
    }

    @Override
    public @NotNull Map<String, String> raw() {
        return values;
    }

    @Override
    public boolean has(@Nullable String name) {
        return !Texts.isBlank(name) && !Texts.isBlank(values.get(Texts.lower(name)));
    }

    @Override
    public @NotNull String getString(@Nullable String name) {
        return getString(name, "");
    }

    @Override
    public @NotNull String getString(@Nullable String name, @NotNull String fallback) {
        if (Texts.isBlank(name)) {
            return fallback;
        }
        String key = Texts.lower(name);
        String value = values.get(key);
        if (!Texts.isBlank(value)) {
            return value;
        }
        String declaredDefault = defaults.get(key);
        return Texts.isBlank(declaredDefault) ? fallback : declaredDefault;
    }

    @Override
    public int getInt(@Nullable String name, int fallback) {
        return ValueParsers.parseInt(getString(name, ""), fallback);
    }

    @Override
    public double getDouble(@Nullable String name, double fallback) {
        return ValueParsers.parseDouble(getString(name, ""), fallback);
    }

    @Override
    public boolean getBoolean(@Nullable String name, boolean fallback) {
        Boolean parsed = ValueParsers.parseBoolean(getString(name, ""));
        return parsed == null ? fallback : parsed;
    }

    @Override
    public long getDurationTicks(@Nullable String name, long fallbackTicks) {
        long parsed = ValueParsers.parseTicks(getString(name, ""));
        return parsed < 0L ? Math.max(0L, fallbackTicks) : parsed;
    }

    @Override
    public double getChance(@Nullable String name, double fallback) {
        String raw = getString(name, "");
        if (Texts.isBlank(raw)) {
            return fallback;
        }
        int slash = raw.indexOf('/');
        if (slash > 0 && slash < raw.length() - 1) {
            Double numerator = ValueParsers.parseDoubleNullable(raw.substring(0, slash).trim());
            Double denominator = ValueParsers.parseDoubleNullable(raw.substring(slash + 1).trim());
            if (numerator != null && denominator != null && denominator != 0D) {
                return numerator / denominator;
            }
            return fallback;
        }
        double parsed = ValueParsers.parseChance(raw);
        return parsed < 0D ? fallback : parsed;
    }

    @Override
    public @NotNull Optional<EntityType> getEntityType(@Nullable String name) {
        String raw = getString(name, "");
        if (Texts.isBlank(raw)) {
            return Optional.empty();
        }
        String normalized = Texts.trim(raw)
                .replace("minecraft:", "")
                .replace('.', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return Optional.of(EntityType.valueOf(normalized));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public @NotNull Optional<Material> getMaterial(@Nullable String name) {
        String raw = getString(name, "");
        if (Texts.isBlank(raw)) {
            return Optional.empty();
        }
        return Optional.ofNullable(Material.matchMaterial(Texts.trim(raw)));
    }

    @Override
    public double getExpression(@Nullable String name, double fallback) {
        String raw = getString(name, "");
        if (Texts.isBlank(raw)) {
            return fallback;
        }
        ExpressionEngine.NumericEvaluationResult evaluated = ExpressionEngine.evaluateNumericDetailed(raw);
        return evaluated.success() ? evaluated.value() : ValueParsers.parseDouble(raw, fallback);
    }
}
