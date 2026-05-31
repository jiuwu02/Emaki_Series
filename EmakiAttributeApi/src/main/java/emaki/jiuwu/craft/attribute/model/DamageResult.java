package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable outcome of an EmakiAttribute damage calculation.
 *
 * <p>Carries the final damage produced by the attribute pipeline along with the
 * diagnostic breakdown (per-stage values, critical flag and the random roll)
 * and the {@link DamageContext} the calculation was performed against.
 *
 * @param damageTypeId  the resolved damage type id; never {@code null}
 * @param finalDamage   the computed final damage; clamped to be {@code >= 0}
 * @param critical      whether the hit resolved as a critical strike
 * @param roll          the random roll used during resolution
 * @param stageValues   per-stage contribution values, keyed by stage id
 * @param damageContext the context the result was computed from; never
 *                      {@code null}
 */
public record DamageResult(String damageTypeId,
        double finalDamage,
        boolean critical,
        double roll,
        Map<String, Double> stageValues,
        DamageContext damageContext) {

    /**
     * Canonical constructor; normalizes {@code null} fields, clamps
     * {@code finalDamage} to be non-negative and defensively copies
     * {@code stageValues}.
     */
    public DamageResult      {
        damageTypeId = damageTypeId == null ? "" : damageTypeId;
        finalDamage = Math.max(0D, finalDamage);
        stageValues = stageValues == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stageValues));
        damageContext = damageContext == null ? DamageContext.empty() : damageContext;
    }

    /** {@return the context variables carried by the backing damage context} */
    public DamageContextVariables variables() {
        return damageContext == null ? DamageContextVariables.empty() : damageContext.variables();
    }

    /** {@return the context variables as a plain map} */
    public Map<String, Object> context() {
        return variables().asMap();
    }
}
