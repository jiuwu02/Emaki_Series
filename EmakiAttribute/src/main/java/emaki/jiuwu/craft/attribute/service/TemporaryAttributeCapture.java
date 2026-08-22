package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.attribute.service.TemporaryAttributeService.TemporaryAttributeMode;

record TemporaryAttributeCapture(long capturedAtMillis,
        Map<String, Double> additiveValues,
        Map<String, Double> setValues,
        String signature,
        List<TemporaryCapturedEffect> effects) {

    private static final TemporaryAttributeCapture EMPTY =
            new TemporaryAttributeCapture(0L, Map.of(), Map.of(), "", List.of());

    static TemporaryAttributeCapture empty() {
        return EMPTY;
    }

    static TemporaryAttributeCapture of(Map<String, TemporaryAttributeGroup> groups, long nowMillis) {
        Map<String, Double> additive = new LinkedHashMap<>();
        Map<String, TemporaryEffect> setWinners = new LinkedHashMap<>();
        List<TemporaryCapturedEffect> captured = new ArrayList<>();
        List<String> signatureParts = new ArrayList<>();
        for (TemporaryAttributeGroup group : groups.values()) {
            if (group == null) {
                continue;
            }
            for (TemporaryEffect effect : group.effects().values()) {
                if (effect == null || effect.expired(nowMillis)) {
                    continue;
                }
                signatureParts.add(group.groupId() + ':' + effect.mode().name() + ':' + effect.attributeId()
                        + ':' + effect.value() + ':' + effect.expiresAtMillis() + ':' + effect.revision());
                captured.add(new TemporaryCapturedEffect(group.groupId(), group.source(), effect));
                if (effect.mode() == TemporaryAttributeMode.ADD) {
                    additive.merge(effect.attributeId(), effect.value(), Double::sum);
                } else {
                    setWinners.merge(effect.attributeId(), effect,
                            (current, candidate) -> candidate.revision() >= current.revision() ? candidate : current);
                }
            }
        }
        if (captured.isEmpty()) {
            return EMPTY;
        }
        Map<String, Double> resolvedSets = new LinkedHashMap<>();
        for (TemporaryEffect winner : setWinners.values()) {
            resolvedSets.put(winner.attributeId(), winner.value());
        }
        signatureParts.sort(String::compareTo);
        return new TemporaryAttributeCapture(nowMillis,
                Map.copyOf(additive),
                Map.copyOf(resolvedSets),
                String.join("|", signatureParts),
                List.copyOf(captured));
    }

    boolean isEmpty() {
        return effects.isEmpty();
    }
}
