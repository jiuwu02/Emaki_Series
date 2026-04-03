package emaki.jiuwu.craft.corelib.assembly;

import java.util.UUID;

import emaki.jiuwu.craft.corelib.assembly.lore.SearchInsertConfig;

public interface AssemblyFeedbackHandler {

    AssemblyFeedbackHandler NO_OP = new AssemblyFeedbackHandler() {
    };

    static AssemblyFeedbackHandler noop() {
        return NO_OP;
    }

    default void onLoreSearchNotFound(UUID playerId, EmakiPresentationEntry entry, SearchInsertConfig config) {
    }

    default void onLoreInvalidRegex(UUID playerId,
            EmakiPresentationEntry entry,
            SearchInsertConfig config,
            String error) {
    }

    default void onLoreInvalidConfig(UUID playerId, EmakiPresentationEntry entry, String error) {
    }
}
