package emaki.jiuwu.craft.attribute.service;

record TemporaryCapturedEffect(String groupId, TemporaryEffectSource source, TemporaryEffect effect) {

    String attributeId() {
        return effect.attributeId();
    }

    double value() {
        return effect.value();
    }

    long remainingTicks(long nowMillis) {
        return effect.remainingTicks(nowMillis);
    }
}
