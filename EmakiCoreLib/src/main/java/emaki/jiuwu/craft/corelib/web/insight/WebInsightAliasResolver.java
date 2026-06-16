package emaki.jiuwu.craft.corelib.web.insight;

public interface WebInsightAliasResolver {

    String idType();

    AliasResolution resolve(String sourceId);

    record AliasResolution(String sourceId, String targetId) {

        public boolean valid() {
            return sourceId != null && !sourceId.isBlank() && targetId != null && !targetId.isBlank();
        }
    }
}
