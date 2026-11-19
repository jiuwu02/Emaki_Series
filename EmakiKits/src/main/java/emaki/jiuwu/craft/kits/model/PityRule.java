package emaki.jiuwu.craft.kits.model;

public record PityRule(int count, String guaranteedId) {

    public PityRule {
        count = Math.max(1, count);
        guaranteedId = guaranteedId == null ? "" : guaranteedId;
    }

    public boolean valid() {
        return !guaranteedId.isBlank();
    }
}
