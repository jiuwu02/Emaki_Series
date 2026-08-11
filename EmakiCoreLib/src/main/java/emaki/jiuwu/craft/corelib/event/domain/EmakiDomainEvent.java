package emaki.jiuwu.craft.corelib.event.domain;

import java.time.Instant;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public class EmakiDomainEvent {

    private final UUID eventId;
    private final String sourceModule;
    private final UUID playerUuid;
    private final Instant timestamp;
    private final EmakiDomainEventPhase phase;
    private final EmakiDomainEventContext context;
    private final boolean cancellable;
    private boolean cancelled;
    private String cancelReason = "";

    public EmakiDomainEvent(String sourceModule,
            UUID playerUuid,
            EmakiDomainEventPhase phase,
            EmakiDomainEventContext context,
            boolean cancellable) {
        this(UUID.randomUUID(), sourceModule, playerUuid, Instant.now(), phase, context, cancellable);
    }

    public EmakiDomainEvent(UUID eventId,
            String sourceModule,
            UUID playerUuid,
            Instant timestamp,
            EmakiDomainEventPhase phase,
            EmakiDomainEventContext context,
            boolean cancellable) {
        this.eventId = eventId == null ? UUID.randomUUID() : eventId;
        this.sourceModule = Texts.isBlank(sourceModule) ? "unknown" : Texts.lower(sourceModule);
        this.playerUuid = playerUuid;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
        this.phase = phase == null ? EmakiDomainEventPhase.DATA : phase;
        this.context = context == null ? EmakiDomainEventContext.empty() : context;
        this.cancellable = cancellable;
    }

    public UUID eventId() {
        return eventId;
    }

    public String sourceModule() {
        return sourceModule;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public EmakiDomainEventPhase phase() {
        return phase;
    }

    public EmakiDomainEventContext context() {
        return context;
    }

    public boolean cancellable() {
        return cancellable;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public String cancelReason() {
        return cancelReason;
    }

    public void cancel(String reason) {
        if (!cancellable) {
            return;
        }
        cancelled = true;
        cancelReason = Texts.toStringSafe(reason);
    }
}
