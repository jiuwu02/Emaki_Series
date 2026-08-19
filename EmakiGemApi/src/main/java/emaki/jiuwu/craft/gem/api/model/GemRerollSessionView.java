package emaki.jiuwu.craft.gem.api.model;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

/** Read-only snapshot of one operator's reroll candidate session. */
public record GemRerollSessionView(@NotNull UUID operatorId,
                                   @NotNull String instanceId,
                                   @NotNull String operationType,
                                   @NotNull List<String> originalAffixes,
                                   @NotNull List<String> candidateAffixes,
                                   int stage,
                                   long version,
                                   long createdAt,
                                   long expiryAt,
                                   @NotNull String terminalState) {

    public GemRerollSessionView {
        instanceId = instanceId == null ? "" : instanceId;
        operationType = operationType == null ? "" : operationType;
        originalAffixes = originalAffixes == null ? List.of() : List.copyOf(originalAffixes);
        candidateAffixes = candidateAffixes == null ? List.of() : List.copyOf(candidateAffixes);
        stage = Math.max(0, stage);
        terminalState = terminalState == null ? "" : terminalState;
    }

    public boolean open() {
        return "open".equals(terminalState);
    }
}
