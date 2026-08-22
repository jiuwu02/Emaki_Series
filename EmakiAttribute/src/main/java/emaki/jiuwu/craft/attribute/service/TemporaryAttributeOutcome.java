package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.model.TemporaryStackMode;
import emaki.jiuwu.craft.attribute.service.TemporaryAttributeService.TemporaryAttributeMode;

public record TemporaryAttributeOutcome(TemporaryAttributeStatus status,
        String groupId,
        String attributeId,
        double value,
        long remainingTicks,
        int affectedCount,
        String detail,
        TemporaryAttributeMode mode,
        TemporaryStackMode stackMode) {

    public TemporaryAttributeOutcome {
        status = status == null ? TemporaryAttributeStatus.INVALID_INPUT : status;
        groupId = groupId == null ? "" : groupId;
        attributeId = attributeId == null ? "" : attributeId;
        detail = detail == null ? "" : detail;
        mode = mode == null ? TemporaryAttributeMode.ADD : mode;
        stackMode = stackMode == null ? TemporaryStackMode.REPLACE : stackMode;
    }

    static TemporaryAttributeOutcome applied(TemporaryAttributeStatus status,
            String groupId,
            TemporaryEffect effect,
            long nowMillis) {
        return new TemporaryAttributeOutcome(status,
                groupId,
                effect.attributeId(),
                effect.value(),
                effect.remainingTicks(nowMillis),
                1,
                "",
                effect.mode(),
                effect.stackMode());
    }

    static TemporaryAttributeOutcome removed(TemporaryAttributeGroup group, long nowMillis) {
        int affectedCount = group.effects().size();
        TemporaryEffect single = affectedCount == 1 ? group.effects().values().iterator().next() : null;
        return new TemporaryAttributeOutcome(TemporaryAttributeStatus.REMOVED,
                group.groupId(),
                single == null ? "" : single.attributeId(),
                single == null ? 0D : single.value(),
                single == null ? 0L : single.remainingTicks(nowMillis),
                affectedCount,
                "",
                single == null ? TemporaryAttributeMode.ADD : single.mode(),
                single == null ? TemporaryStackMode.REPLACE : single.stackMode());
    }

    static TemporaryAttributeOutcome removedByTag(String tag, int affectedCount) {
        return new TemporaryAttributeOutcome(TemporaryAttributeStatus.REMOVED, "", "", 0D, 0L, affectedCount, tag,
                TemporaryAttributeMode.ADD, TemporaryStackMode.REPLACE);
    }

    static TemporaryAttributeOutcome notFound(String groupId) {
        return new TemporaryAttributeOutcome(TemporaryAttributeStatus.NOT_FOUND, groupId, "", 0D, 0L, 0, "",
                TemporaryAttributeMode.ADD, TemporaryStackMode.REPLACE);
    }

    static TemporaryAttributeOutcome noMatch(String groupId, String detail) {
        return new TemporaryAttributeOutcome(TemporaryAttributeStatus.NO_MATCH, groupId, "", 0D, 0L, 0, detail,
                TemporaryAttributeMode.ADD, TemporaryStackMode.REPLACE);
    }

    static TemporaryAttributeOutcome rejected(TemporaryAttributeStatus status,
            String groupId,
            String attributeId,
            String detail) {
        return new TemporaryAttributeOutcome(status, groupId, attributeId, 0D, 0L, 0, detail,
                TemporaryAttributeMode.ADD, TemporaryStackMode.REPLACE);
    }

    public boolean successful() {
        return status.successful();
    }

    public String reasonKey() {
        return "action.stage.attribute.temporary_" + status.reasonSuffix();
    }
}
