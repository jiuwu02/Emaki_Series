package emaki.jiuwu.craft.dungeon.random.gen;

import java.util.List;

public final class Assigner {

    public record TemplateOption(String typeId, List<String> tags, int weight, int minCount, int maxCount,
            int anchorCount) {

        public TemplateOption {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }

        public boolean carries(String tag) {
            return tags.contains(tag);
        }

        /** 该模板的门锚点数是否够接出 degree 条连接。 */
        public boolean affords(int degree) {
            return anchorCount >= degree;
        }
    }

    public record MandatoryRule(String tag, int minCount, int maxCount, String placementRule, int percentile) {
    }
}
