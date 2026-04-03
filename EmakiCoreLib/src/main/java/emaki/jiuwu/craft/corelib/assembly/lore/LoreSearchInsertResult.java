package emaki.jiuwu.craft.corelib.assembly.lore;

import java.util.ArrayList;
import java.util.List;

public record LoreSearchInsertResult(List<String> loreLines, LoreSearchInsertStatus status, String detail) {

    public LoreSearchInsertResult {
        List<String> normalized = new ArrayList<>();
        if (loreLines != null) {
            normalized.addAll(loreLines);
        }
        loreLines = normalized.isEmpty() ? List.of() : List.copyOf(normalized);
        status = status == null ? LoreSearchInsertStatus.INVALID_CONFIG : status;
        detail = detail == null ? "" : detail;
    }

    public boolean mutated() {
        return status == LoreSearchInsertStatus.APPLIED || status == LoreSearchInsertStatus.APPENDED_TO_END;
    }
}
