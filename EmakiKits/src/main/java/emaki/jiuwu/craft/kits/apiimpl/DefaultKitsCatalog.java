package emaki.jiuwu.craft.kits.apiimpl;

import emaki.jiuwu.craft.kits.EmakiKitsPlugin;
import emaki.jiuwu.craft.kits.api.KitCatalog;
import emaki.jiuwu.craft.kits.api.model.KitTypeView;
import emaki.jiuwu.craft.kits.api.model.KitView;
import emaki.jiuwu.craft.kits.join.PlaytimeTracker;
import emaki.jiuwu.craft.kits.limit.TimeWindow;
import emaki.jiuwu.craft.kits.model.KitDefinition;
import emaki.jiuwu.craft.kits.model.RewardEntry;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;

public final class DefaultKitsCatalog implements KitCatalog {

    private final EmakiKitsPlugin plugin;

    public DefaultKitsCatalog(EmakiKitsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Set<String> kitIds() {
        return Set.copyOf(plugin.appConfig().kits().keySet());
    }

    @Override
    public @NotNull Optional<KitView> kit(@Nullable String kitId) {
        KitDefinition def = plugin.kit(kitId == null ? "" : kitId.toLowerCase(Locale.ROOT));
        return def == null ? Optional.empty() : Optional.of(toView(def));
    }

    @Override
    public boolean withinTimeWindow(@Nullable String kitId) {
        KitDefinition def = plugin.kit(kitId == null ? "" : kitId.toLowerCase(Locale.ROOT));
        if (def == null) {
            return false;
        }
        if (!def.hasTimeLimit()) {
            return true;
        }
        KitDefinition.LimitRule limit = def.limit();
        String mode = limit.mode().toLowerCase(Locale.ROOT);
        try {
            return switch (mode) {
                case "range" -> {
                    Long start = parseEpoch(limit.start());
                    Long end = parseEpoch(limit.end());
                    yield start == null || end == null
                            || TimeWindow.range(start, end).contains(System.currentTimeMillis());
                }
                case "cron" -> TimeWindow.cron(limit.cron(), limit.durationSeconds(),
                        limit.timeZone()).contains(System.currentTimeMillis());
                default -> true;
            };
        } catch (RuntimeException exception) {
            return true;
        }
    }

    @Override
    public long playedSeconds(@Nullable Player player) {
        return player == null ? 0L : PlaytimeTracker.playedSeconds(player);
    }

    private static KitView toView(KitDefinition def) {
        List<String> rewardIds = new ArrayList<>();
        for (RewardEntry entry : def.fixedRewards().entries()) {
            rewardIds.add(entry.id());
        }
        for (RewardEntry entry : def.randomRewards().entries()) {
            rewardIds.add(entry.id());
        }
        return new KitView(def.id(), KitTypeView.valueOf(def.type().name()), def.enabled(),
                def.displayName(), def.permission(), def.hasTimeLimit(),
                def.choice().minSelect(), def.choice().maxSelect(),
                def.hasPurchase(), List.copyOf(rewardIds));
    }

    private static Long parseEpoch(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim()).toEpochMilli();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
