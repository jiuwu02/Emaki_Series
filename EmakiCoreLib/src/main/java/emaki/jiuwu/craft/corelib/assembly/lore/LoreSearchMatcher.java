package emaki.jiuwu.craft.corelib.assembly.lore;

import java.util.List;

public interface LoreSearchMatcher {

    List<Integer> matchAll(List<String> loreLines, String pattern, boolean ignoreCase);
}
