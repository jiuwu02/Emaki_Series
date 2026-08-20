package emaki.jiuwu.craft.gem.api.model;

import org.jetbrains.annotations.NotNull;

/** Result of opening, confirming, or abandoning one gem reroll operation. */
public record GemRerollOutcome(@NotNull String operationId,
                               @NotNull String terminalState,
                               @NotNull String terminalReason,
                               boolean compensationPending,
                               @NotNull GemRerollSessionView session) {

    public GemRerollOutcome {
        operationId = operationId == null ? "" : operationId;
        terminalState = terminalState == null ? "" : terminalState;
        terminalReason = terminalReason == null ? "" : terminalReason;
        if (session == null) {
            throw new NullPointerException("session");
        }
    }
}
