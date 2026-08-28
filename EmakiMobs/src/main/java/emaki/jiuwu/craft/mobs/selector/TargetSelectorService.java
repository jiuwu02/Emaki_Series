package emaki.jiuwu.craft.mobs.selector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;

public final class TargetSelectorService {

    private final MobIdentifier mobIdentifier;
    private final Supplier<Map<String, MobSpec>> mobRegistry;
    private final Supplier<TargetSelectorConfig> selectorConfig;
    private final ScoreSnapshotService snapshots;
    private final BiFunction<UUID, UUID, Double> threatLookup;
    private final Function<UUID, UUID> firstDamagerLookup;
    private final Function<UUID, UUID> lastDamagerLookup;

    public TargetSelectorService(MobIdentifier mobIdentifier,
            Supplier<Map<String, MobSpec>> mobRegistry,
            Supplier<TargetSelectorConfig> selectorConfig,
            ScoreSnapshotService snapshots,
            BiFunction<UUID, UUID, Double> threatLookup,
            Function<UUID, UUID> firstDamagerLookup,
            Function<UUID, UUID> lastDamagerLookup) {
        this.mobIdentifier = mobIdentifier;
        this.mobRegistry = mobRegistry;
        this.selectorConfig = selectorConfig;
        this.snapshots = snapshots;
        this.threatLookup = threatLookup;
        this.firstDamagerLookup = firstDamagerLookup;
        this.lastDamagerLookup = lastDamagerLookup;
    }

    @Nullable
    public Player select(LivingEntity mob, String selectorId) {
        TargetSelectorConfig config = selectorConfig.get();
        SelectorDefinition definition = config == null ? null : config.selectors().get(selectorId);
        if (definition == null || !mob.isValid()) {
            return null;
        }
        double range = resolveRange(mob, definition);
        List<Player> candidates = candidates(mob, definition, range);
        if (candidates.isEmpty()) {
            return null;
        }
        return switch (definition.mode()) {
            case FIRST_DAMAGER -> ledgerTarget(candidates, firstDamagerLookup.apply(mob.getUniqueId()));
            case LAST_DAMAGER -> ledgerTarget(candidates, lastDamagerLookup.apply(mob.getUniqueId()));
            case RANDOM -> candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            case HIGHEST -> extreme(mob, definition, candidates, Comparator.naturalOrder());
            case LOWEST -> extreme(mob, definition, candidates, Comparator.reverseOrder());
            case WEIGHTED_RANDOM -> weightedRandom(mob, definition, candidates);
        };
    }

    private double resolveRange(LivingEntity mob, SelectorDefinition definition) {
        if (definition.range() >= 0D) {
            return definition.range();
        }
        String mobId = mobIdentifier.readId(mob);
        MobSpec spec = mobId == null ? null : mobRegistry.get().get(mobId);
        if (spec != null && spec.threatConfig() != null) {
            return Math.max(0D, spec.threatConfig().maxRange());
        }
        return 16D;
    }

    private List<Player> candidates(LivingEntity mob,
            SelectorDefinition definition,
            double range) {
        List<Player> candidates = new ArrayList<>();
        for (var entity : mob.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof Player player) || !passesFilter(definition, player)) {
                continue;
            }
            candidates.add(player);
        }
        return candidates;
    }

    private boolean passesFilter(SelectorDefinition definition, Player player) {
        if (definition.filter() == null || !definition.filter().configured()) {
            return true;
        }
        PlayerScoreSnapshot snapshot = snapshots.snapshot(player.getUniqueId());
        return snapshot != null && snapshot.filterResults().getOrDefault(definition.id(), false);
    }

    @Nullable
    private Player ledgerTarget(List<Player> candidates, UUID playerId) {
        if (playerId == null) {
            return null;
        }
        for (Player candidate : candidates) {
            if (candidate.getUniqueId().equals(playerId)) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private Player extreme(LivingEntity mob,
            SelectorDefinition definition,
            List<Player> candidates,
            Comparator<Double> comparator) {
        if (definition.score().isEmpty()) {
            return null;
        }
        Player selected = null;
        double selectedScore = 0D;
        for (Player candidate : candidates) {
            double score = score(mob, candidate, definition.score());
            if (selected == null || comparator.compare(score, selectedScore) > 0) {
                selected = candidate;
                selectedScore = score;
            }
        }
        return selected;
    }

    @Nullable
    private Player weightedRandom(LivingEntity mob,
            SelectorDefinition definition,
            List<Player> candidates) {
        if (definition.score().isEmpty()) {
            return null;
        }
        List<ScoredPlayer> weighted = new ArrayList<>();
        double total = 0D;
        for (Player candidate : candidates) {
            double score = score(mob, candidate, definition.score());
            if (!Double.isFinite(score) || score <= 0D) {
                continue;
            }
            weighted.add(new ScoredPlayer(candidate, score));
            total += score;
        }
        if (weighted.isEmpty() || !Double.isFinite(total) || total <= 0D) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (ScoredPlayer entry : weighted) {
            roll -= entry.score();
            if (roll < 0D) {
                return entry.player();
            }
        }
        return weighted.getLast().player();
    }

    private double score(LivingEntity mob, Player player, List<ScoreTerm> terms) {
        PlayerScoreSnapshot snapshot = snapshots.snapshot(player.getUniqueId());
        double score = 0D;
        for (ScoreTerm term : terms) {
            double value = switch (term) {
                case ScoreTerm.ThreatTerm ignored -> threatLookup.apply(
                        mob.getUniqueId(), player.getUniqueId());
                case ScoreTerm.DistanceTerm ignored -> distance(mob, snapshot);
                case ScoreTerm.HealthTerm ignored -> snapshot == null ? 0D : snapshot.health();
                case ScoreTerm.EquipmentTerm equipment -> snapshot == null
                        ? 0D : snapshot.equipmentScores().getOrDefault(equipment.tableId(), 0D);
                case ScoreTerm.ExpressionTerm expression -> snapshot == null
                        ? 0D : snapshot.expressionScores().getOrDefault(expression.expressionId(), 0D);
            };
            double contribution = value * term.factor();
            if (Double.isFinite(contribution)) {
                score += contribution;
            }
        }
        return Double.isFinite(score) ? score : 0D;
    }

    private double distance(LivingEntity mob, PlayerScoreSnapshot snapshot) {
        if (snapshot == null || !mob.getWorld().getUID().equals(snapshot.worldId())) {
            return 0D;
        }
        var location = mob.getLocation();
        double dx = location.getX() - snapshot.x();
        double dy = location.getY() - snapshot.y();
        double dz = location.getZ() - snapshot.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private record ScoredPlayer(Player player, double score) {
    }
}
