package emaki.jiuwu.craft.codex.codex.model;

public record CodexEntryState(long unlockedAt, boolean activated, boolean claimed) {

    public static CodexEntryState unlockedNow(long timestamp) {
        return new CodexEntryState(timestamp, false, false);
    }

    public CodexEntryState withActivated(boolean value) {
        return new CodexEntryState(unlockedAt, value, claimed);
    }

    public CodexEntryState withClaimed(boolean value) {
        return new CodexEntryState(unlockedAt, activated, value);
    }
}
