package emaki.jiuwu.craft.skills.protocol;

public record SkillPdcMutation(
        String operation,
        RawSnapshot before,
        RawSnapshot after,
        boolean committed,
        String reason) {

    public SkillPdcMutation {
        operation = operation == null ? "" : operation;
        before = before == null ? RawSnapshot.empty() : before;
        after = after == null ? RawSnapshot.empty() : after;
        reason = reason == null ? "" : reason;
    }
}
