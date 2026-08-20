package emaki.jiuwu.craft.item.service;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.item.api.ItemStateKey;
import emaki.jiuwu.craft.item.api.ItemStateMetadata;
import emaki.jiuwu.craft.item.api.ItemStateSnapshot;
import emaki.jiuwu.craft.item.model.ItemStateConfig;

public final class ItemStatePreservationService {

    public enum Outcome {
        SKIPPED,
        INTACT,
        RESTORED,
        LOST
    }

    private final EmakiItemStateService stateService;
    private final Supplier<DebugLogger> debugLoggerSupplier;

    public ItemStatePreservationService(EmakiItemStateService stateService,
            Supplier<DebugLogger> debugLoggerSupplier) {
        this.stateService = stateService;
        this.debugLoggerSupplier = debugLoggerSupplier;
    }

    public ItemStateSnapshot capture(ItemStack original) {
        if (original == null || original.getType().isAir()) {
            return null;
        }
        ItemStateConfig config = stateService.config();
        if (!config.preservation().verifyRebuild()) {
            return null;
        }
        ItemStateSnapshot snapshot = stateService.snapshot(original);
        return snapshot.values().isEmpty() && snapshot.metadata().instanceId().isBlank() ? null : snapshot;
    }

    public Outcome reapply(ItemStack rebuilt, ItemStateSnapshot captured, String reason) {
        if (captured == null || rebuilt == null || rebuilt.getType().isAir()) {
            return Outcome.SKIPPED;
        }
        if (intact(rebuilt, captured)) {
            return Outcome.INTACT;
        }
        boolean restored = stateService.restoreSnapshot(rebuilt, captured);
        Outcome outcome = restored ? Outcome.RESTORED : Outcome.LOST;
        logOutcome(null, reason, outcome, captured.values().size());
        return outcome;
    }

    public Outcome repairBoundary(ItemStack item, Player holder, String reason) {
        if (item == null || item.getType().isAir()) {
            return Outcome.SKIPPED;
        }
        ItemStateSnapshot before = stateService.snapshot(item);
        if (before.values().isEmpty()) {
            return Outcome.SKIPPED;
        }
        if (before.metadata().valid()) {
            return Outcome.INTACT;
        }
        ItemStateSnapshot after = stateService.repair(item);
        Outcome outcome = after.metadata().valid() ? Outcome.RESTORED : Outcome.LOST;
        logOutcome(holder, reason, outcome, before.values().size());
        return outcome;
    }

    private boolean intact(ItemStack rebuilt, ItemStateSnapshot captured) {
        ItemStateSnapshot current = stateService.snapshot(rebuilt);
        for (Map.Entry<ItemStateKey<?>, Object> entry : captured.values().entrySet()) {
            if (!Objects.equals(entry.getValue(), current.values().get(entry.getKey()))) {
                return false;
            }
        }
        ItemStateMetadata expected = captured.metadata();
        ItemStateMetadata actual = current.metadata();
        if (expected.instanceId().isBlank()) {
            return true;
        }
        return expected.instanceId().equals(actual.instanceId()) && actual.revision() >= expected.revision();
    }

    private void logOutcome(Player holder, String reason, Outcome outcome, int fieldCount) {
        DebugLogger debugLogger = debugLoggerSupplier == null ? null : debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("item_state", holder)) {
            return;
        }
        debugLogger.log("item_state", holder, "item_state.preserved", Map.of(
                "reason", reason == null ? "" : reason,
                "outcome", outcome.name().toLowerCase(Locale.ROOT),
                "fields", fieldCount));
    }
}
