package emaki.jiuwu.craft.kits.service;

import emaki.jiuwu.craft.kits.config.AppConfig;
import emaki.jiuwu.craft.kits.config.KitIds;
import emaki.jiuwu.craft.kits.cycle.PeriodKeys;
import emaki.jiuwu.craft.kits.limit.TimeWindow;
import emaki.jiuwu.craft.kits.model.ChoiceOption;
import emaki.jiuwu.craft.kits.model.ClaimResult;
import emaki.jiuwu.craft.corelib.schedule.cron.CronExpression;
import emaki.jiuwu.craft.kits.model.KitDefinition;
import emaki.jiuwu.craft.kits.model.KitType;
import emaki.jiuwu.craft.kits.model.OpenMilestone;
import emaki.jiuwu.craft.kits.model.RewardEntry;
import emaki.jiuwu.craft.kits.reward.EconomyPort;
import emaki.jiuwu.craft.kits.reward.RewardDeliveryException;
import emaki.jiuwu.craft.kits.reward.RewardDispatcher;
import emaki.jiuwu.craft.kits.store.KitState;
import emaki.jiuwu.craft.kits.store.KitStore;
import emaki.jiuwu.craft.kits.store.StoreKeys;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

public final class KitService {

    private final KitStore store;
    private final RewardDispatcher rewards;
    private final EconomyPort economy;
    private final Supplier<AppConfig> config;
    private final Function<String, KitDefinition> kits;

    public KitService(KitStore store, RewardDispatcher rewards, EconomyPort economy,
                      Supplier<AppConfig> config,
                      Function<String, KitDefinition> kits) {
        this.store = store;
        this.rewards = rewards;
        this.economy = economy;
        this.config = config;
        this.kits = kits;
    }

    public ClaimResult claim(Player player, String rawKitId) {
        return claim(player, rawKitId, List.of(), false);
    }

    public ClaimResult claim(Player player, String rawKitId, List<String> selections) {
        return claim(player, rawKitId, selections, false);
    }

    public ClaimResult claim(Player player, String rawKitId, List<String> selections, boolean makeup) {
        String kitId = KitIds.normalize(rawKitId);
        KitDefinition def = kits.apply(kitId);
        if (def == null || !def.enabled()) {
            return ClaimResult.failure(ClaimResult.Status.CONFIG_ERROR, "error.kit.unknown", kitId, "once");
        }
        if (!player.hasPermission(def.permission())) {
            return ClaimResult.failure(ClaimResult.Status.PERMISSION_DENIED, "error.permission.denied", kitId, "once");
        }
        if (def.type() == KitType.CDK) {
            return ClaimResult.failure(ClaimResult.Status.CONDITION_FAILED, "error.kit.cdk_use_redeem", kitId, "once");
        }
        if (def.type() == KitType.INVITE) {
            return ClaimResult.failure(ClaimResult.Status.CONDITION_FAILED, "error.kit.invite_use_bind", kitId, "once");
        }
        if (makeup && (!def.isCyclic() || !def.cycle().allowMakeup())) {
            return ClaimResult.failure(ClaimResult.Status.CONDITION_FAILED, "error.kit.makeup_disabled", kitId, "once");
        }
        TimeWindow window = timeWindow(def);
        if (!window.contains(System.currentTimeMillis())) {
            return ClaimResult.failure(ClaimResult.Status.CONDITION_FAILED, "error.kit.outside_time_window", kitId, "once");
        }
        String periodKey = makeup ? previousKey(def) : periodKey(def);
        if (def.maxClaimsTotal() > 0) {
            long total = store.read(s -> s.claimTotals().getOrDefault(
                    KitState.countKey("claim", kitId, player.getUniqueId()), 0L));
            if (total >= def.maxClaimsTotal()) {
                return ClaimResult.failure(ClaimResult.Status.LIMIT_EXCEEDED, "error.kit.total_limit", kitId, periodKey);
            }
        }
        if (def.type() == KitType.CHOICE) {
            ClaimResult selectionCheck = validateSelections(player, def, selections, periodKey);
            if (!selectionCheck.success()) {
                return selectionCheck;
            }
        }
        if (def.hasPurchase()) {
            long purchased = store.read(s -> s.purchaseCounts().getOrDefault(
                    KitState.countKey("purchase", kitId, player.getUniqueId()), 0L));
            if (purchased <= 0L) {
                return ClaimResult.failure(ClaimResult.Status.CONDITION_FAILED, "error.kit.not_purchased", kitId, periodKey);
            }
        }
        String claimKey = StoreKeys.claim(kitId, player.getUniqueId(), periodKey);
        List<String> auditIds = new ArrayList<>();
        String transactionId = store.read(s -> "T" + s.nextSequence());
        boolean reserved = store.claimOnce(claimKey, () -> {});
        if (!reserved) {
            return ClaimResult.failure(ClaimResult.Status.ALREADY_CLAIMED, "error.kit.already_claimed", kitId, periodKey);
        }
        try {
            List<RewardEntry> fixed = new ArrayList<>(def.fixedRewards().entries());
            if (def.type() == KitType.CHOICE) {
                Map<String, ChoiceOption> known = new LinkedHashMap<>();
                for (ChoiceOption option : def.choice().options()) {
                    known.put(option.optionId(), option);
                }
                for (String raw : selections) {
                    ChoiceOption option = known.get(KitIds.normalize(raw));
                    if (option != null) {
                        fixed.addAll(rewards.optionRewards(option));
                    }
                }
            }
            auditIds.addAll(rewards.deliver(player.getUniqueId(), player, kitId,
                    fixed, transactionId, 0));
            if (def.randomRewards().weighted()) {
                auditIds.addAll(rewards.deliverGroup(player.getUniqueId(), player, kitId,
                        def.randomRewards(), transactionId, 0));
            } else {
                auditIds.addAll(rewards.deliver(player.getUniqueId(), player, kitId,
                        def.randomRewards().entries(), transactionId, 0));
            }
        } catch (RuntimeException exception) {
            rollback(claimKey, kitId, player.getUniqueId(), periodKey, transactionId);
            return ClaimResult.failure(ClaimResult.Status.STORAGE_FAILED, "error.reward.delivery_failed", kitId, periodKey);
        }
        if (def.type() == KitType.CHOICE) {
            store.mutate(s -> s.selections().put(
                    StoreKeys.selection(kitId, player.getUniqueId(), periodKey),
                    String.join(",", selections)));
        }
        store.mutate(s -> {
            String totalKey = KitState.countKey("claim", kitId, player.getUniqueId());
            s.claimTotals().merge(totalKey, 1L, Long::sum);
            if (def.hasPurchase()) {
                String purchaseKey = KitState.countKey("purchase", kitId, player.getUniqueId());
                s.purchaseCounts().computeIfPresent(purchaseKey, (k, v) -> v > 0 ? v - 1 : 0L);
            }
        });
        if (def.hasOpenCount()) {
            return applyOpenCount(player, def, periodKey, transactionId, auditIds, makeup);
        }
        store.recordAudit(new KitState.AuditRecord(
                store.read(s -> "A" + s.nextSequence()), System.currentTimeMillis(),
                player.getUniqueId(), kitId, makeup ? "claim_makeup" : "claim_success",
                "transaction=" + transactionId + ", period=" + periodKey));
        return ClaimResult.success(kitId, periodKey, auditIds);
    }

    public ClaimResult redeem(Player player, String rawKitId, String rawCode) {
        String kitId = KitIds.normalize(rawKitId);
        KitDefinition def = kits.apply(kitId);
        if (def == null || !def.enabled() || def.type() != KitType.CDK) {
            return ClaimResult.failure(ClaimResult.Status.CONFIG_ERROR, "error.kit.unknown", kitId, "once");
        }
        if (!player.hasPermission("emakikits.redeem")) {
            return ClaimResult.failure(ClaimResult.Status.PERMISSION_DENIED, "error.permission.denied", kitId, "once");
        }
        String code = config.get().cdk().caseSensitive() ? rawCode : rawCode.toLowerCase(Locale.ROOT);
        List<String> known = def.cdk().codes().stream()
                .map(c -> config.get().cdk().caseSensitive() ? c : c.toLowerCase(Locale.ROOT))
                .toList();
        if (!known.contains(code)) {
            return ClaimResult.failure(ClaimResult.Status.CONDITION_FAILED, "error.cdk.invalid", kitId, "once");
        }
        if (store.read(s -> s.cdkDisabled().contains(code))) {
            return ClaimResult.failure(ClaimResult.Status.CDK_EXHAUSTED, "error.cdk.disabled", kitId, "once");
        }
        boolean globalOnce = def.cdk().mode() == KitDefinition.CdkRule.CdkMode.GLOBAL_ONCE;
        String idempotencyKey = globalOnce
                ? StoreKeys.cdkGlobal(code)
                : StoreKeys.cdkPlayer(code, player.getUniqueId());
        String periodKey = "cdk:" + code;
        String transactionId = store.read(s -> "T" + s.nextSequence());
        List<String> auditIds = new ArrayList<>();
        String countKey = StoreKeys.cdkCount(code, player.getUniqueId());
        long perPlayerLimit = def.cdk().perPlayerLimit();
        if (globalOnce) {
            boolean reserved = store.claimOnce(idempotencyKey, () -> {});
            if (!reserved) {
                return ClaimResult.failure(ClaimResult.Status.CDK_EXHAUSTED, "error.cdk.exhausted", kitId, periodKey);
            }
        } else {
            boolean[] reserved = {false};
            store.mutate(s -> {
                long used = s.cdkRedeemCounts().getOrDefault(countKey, 0L);
                if (used >= perPlayerLimit) {
                    return;
                }
                s.cdkRedeemCounts().put(countKey, used + 1);
                reserved[0] = true;
            });
            if (!reserved[0]) {
                return ClaimResult.failure(ClaimResult.Status.CDK_EXHAUSTED, "error.cdk.player_limit", kitId, periodKey);
            }
        }
        try {
            List<RewardEntry> payload = new ArrayList<>(def.fixedRewards().entries());
            payload.addAll(def.randomRewards().entries());
            auditIds.addAll(rewards.deliver(player.getUniqueId(), player, kitId,
                    payload, transactionId, 0));
        } catch (RuntimeException exception) {
            if (globalOnce) {
                store.mutate(s -> s.idempotencyKeys().remove(idempotencyKey));
            } else {
                store.mutate(s -> s.cdkRedeemCounts().computeIfPresent(countKey, (k, v) -> v > 1 ? v - 1 : null));
            }
            return ClaimResult.failure(ClaimResult.Status.STORAGE_FAILED, "error.reward.delivery_failed", kitId, periodKey);
        }
        store.recordAudit(new KitState.AuditRecord(
                store.read(s -> "A" + s.nextSequence()), System.currentTimeMillis(),
                player.getUniqueId(), kitId, "cdk_redeem",
                "transaction=" + transactionId + ", code=" + code + ", mode="
                        + def.cdk().mode() + ", globalOnce=" + globalOnce));
        return ClaimResult.success(kitId, periodKey, auditIds);
    }

    public ClaimResult buy(Player player, String rawKitId) {
        String kitId = KitIds.normalize(rawKitId);
        KitDefinition def = kits.apply(kitId);
        if (def == null || !def.enabled() || !def.hasPurchase()) {
            return ClaimResult.failure(ClaimResult.Status.CONFIG_ERROR, "error.kit.purchase_disabled", kitId, "once");
        }
        if (!player.hasPermission("emakikits.buy")) {
            return ClaimResult.failure(ClaimResult.Status.PERMISSION_DENIED, "error.permission.denied", kitId, "once");
        }
        String countKey = KitState.countKey("purchase_total", kitId, player.getUniqueId());
        long bought = store.read(s -> s.purchaseCounts().getOrDefault(countKey, 0L));
        if (def.purchase().maxPurchases() > 0 && bought >= def.purchase().maxPurchases()) {
            return ClaimResult.failure(ClaimResult.Status.LIMIT_EXCEEDED, "error.kit.purchase_limit", kitId, "once");
        }
        AppConfig.EconomySettings settings = new AppConfig.EconomySettings(
                def.purchase().economyProvider(), def.purchase().currencyId());
        double price = def.purchase().price();
        if (price > 0D) {
            if (!economy.available()) {
                return ClaimResult.failure(ClaimResult.Status.PAYMENT_FAILED, "error.economy.unavailable", kitId, "once");
            }
            if (economy.balance(player, settings) + 1.0E-9D < price) {
                return ClaimResult.failure(ClaimResult.Status.PAYMENT_FAILED, "error.economy.insufficient", kitId, "once");
            }
        }
        String transactionId = store.read(s -> "T" + s.nextSequence());
        String debitKey = "purchase|" + kitId + "|" + player.getUniqueId() + "|" + transactionId;
        boolean reserved = store.claimOnce(debitKey, () -> {});
        if (!reserved) {
            return ClaimResult.failure(ClaimResult.Status.ALREADY_CLAIMED, "error.purchase.duplicate", kitId, "once");
        }
        if (price > 0D && !economy.debit(player, settings, price)) {
            store.mutate(s -> s.idempotencyKeys().remove(debitKey));
            return ClaimResult.failure(ClaimResult.Status.PAYMENT_FAILED, "error.economy.debit_failed", kitId, "once");
        }
        store.mutate(s -> {
            String availableKey = KitState.countKey("purchase", kitId, player.getUniqueId());
            s.purchaseCounts().merge(availableKey, 1L, Long::sum);
            s.purchaseCounts().merge(countKey, 1L, Long::sum);
        });
        store.recordAudit(new KitState.AuditRecord(
                store.read(s -> "A" + s.nextSequence()), System.currentTimeMillis(),
                player.getUniqueId(), kitId, "purchase",
                "transaction=" + transactionId + ", price=" + price + ", provider=" + settings.provider()));
        return ClaimResult.success(kitId, "purchase", List.of());
    }

    public ClaimResult bindInvite(Player inviter, UUID inviteeId, boolean inviteeNew) {
        if (!inviter.hasPermission("emakikits.invite")) {
            return ClaimResult.failure(ClaimResult.Status.PERMISSION_DENIED, "error.permission.denied", "invite", "once");
        }
        AppConfig.InviteSettings settings = config.get().invite();
        if (!settings.enabled()) {
            return ClaimResult.failure(ClaimResult.Status.CONFIG_ERROR, "error.invite.disabled", "invite", "once");
        }
        if (inviter.getUniqueId().equals(inviteeId)) {
            return ClaimResult.failure(ClaimResult.Status.CONDITION_FAILED, "error.invite.self", "invite", "once");
        }
        if (settings.requireNewPlayer() && !inviteeNew) {
            return ClaimResult.failure(ClaimResult.Status.CONDITION_FAILED, "error.invite.not_new", "invite", "once");
        }
        ClaimResult bindable = checkBindable(inviter.getUniqueId(), inviteeId);
        if (!bindable.success()) {
            return bindable;
        }
        if (!settings.allowMutual()) {
            boolean inviterIsBound = store.read(s -> s.inviteBinds().containsKey(inviter.getUniqueId()));
            if (inviterIsBound) {
                return ClaimResult.failure(ClaimResult.Status.CONDITION_FAILED, "error.invite.mutual", "invite", "once");
            }
        }
        if (!completeBind(inviter.getUniqueId(), inviteeId, inviter.getUniqueId(), "bind",
                "invitee=" + inviteeId + ", new=" + inviteeNew)) {
            return ClaimResult.failure(ClaimResult.Status.ALREADY_CLAIMED, "error.invite.already_bound", "invite", "once");
        }
        grantInviteReward(inviter.getUniqueId(), inviteeId);
        return ClaimResult.success("invite", "bind", List.of());
    }

    private ClaimResult checkBindable(UUID inviterId, UUID inviteeId) {
        List<UUID> current = store.read(s -> s.inviteBinds().getOrDefault(inviteeId, List.of()));
        if (current.contains(inviterId)) {
            return ClaimResult.failure(ClaimResult.Status.ALREADY_CLAIMED, "error.invite.already_bound", "invite", "once");
        }
        int maxBinds = inviteBindLimit();
        if (current.size() >= maxBinds) {
            return maxBinds <= 1
                    ? ClaimResult.failure(ClaimResult.Status.ALREADY_CLAIMED, "error.invite.already_bound", "invite", "once")
                    : ClaimResult.failure(ClaimResult.Status.LIMIT_EXCEEDED, "error.invite.bind_limit", "invite", "once");
        }
        return ClaimResult.success("invite", "bind", List.of());
    }

    private boolean completeBind(UUID inviterId, UUID inviteeId, UUID auditPlayer,
                                 String auditAction, String auditDetail) {
        String pairKey = StoreKeys.bindPair(inviteeId, inviterId);
        boolean reserved = store.claimOnce(pairKey, () -> {});
        if (!reserved) {
            return false;
        }
        store.mutate(s -> s.inviteBinds().computeIfAbsent(inviteeId, key -> new ArrayList<>()).add(inviterId));
        store.recordAudit(new KitState.AuditRecord(
                store.read(s -> "A" + s.nextSequence()), System.currentTimeMillis(),
                auditPlayer, "invite", auditAction, auditDetail));
        return true;
    }

    private int inviteBindLimit() {
        KitDefinition inviteKit = findFirstOfType(KitType.INVITE);
        if (inviteKit != null) {
            return Math.max(1, inviteKit.invite().maxBindsPerInvitee());
        }
        return Math.max(1, config.get().invite().maxBindsPerInvitee());
    }

    private void grantInviteReward(UUID inviterId, UUID inviteeId) {
        KitDefinition inviteKit = findFirstOfType(KitType.INVITE);
        if (inviteKit == null || !inviteKit.enabled()) {
            return;
        }
        String target = inviteKit.invite().rewardTarget();
        UUID rewardPlayer = "INVITEE".equals(target) ? inviteeId : inviterId;
        Player online = org.bukkit.Bukkit.getPlayer(rewardPlayer);
        if (online == null) {
            return;
        }
        String pairKey = StoreKeys.claim(inviteKit.id(), rewardPlayer, "invite:" + inviterId + ":" + inviteeId);
        String legacyKey = StoreKeys.claim(inviteKit.id(), rewardPlayer, "invite:" + inviteeId);
        boolean legacyCovered = store.read(s -> s.idempotencyKeys().contains(legacyKey));
        if (legacyCovered) {
            return;
        }
        String transactionId = store.read(s -> "T" + s.nextSequence());
        boolean rewardReserved = store.claimOnce(pairKey, () -> {});
        if (!rewardReserved) {
            return;
        }
        try {
            List<RewardEntry> payload = new ArrayList<>(inviteKit.fixedRewards().entries());
            payload.addAll(inviteKit.randomRewards().entries());
            rewards.deliver(rewardPlayer, online, inviteKit.id(), payload, transactionId, 0);
        } catch (RuntimeException exception) {
            store.mutate(s -> s.idempotencyKeys().remove(pairKey));
        }
    }

    public ClaimResult onJoin(Player player, boolean firstJoin, String worldName) {
        return onJoin(player, firstJoin, worldName, null);
    }

    public ClaimResult onJoin(Player player, boolean firstJoin, String worldName, UUID inviterId) {
        for (KitDefinition def : config.get().kits().values()) {
            if (def.type() != KitType.JOIN || !def.enabled()) {
                continue;
            }
            if (!worldAllowed(def, worldName)) {
                continue;
            }
            boolean shouldFire = (def.join().onFirstJoin() && firstJoin)
                    || def.join().onEverySpecifiedJoin();
            if (!shouldFire) {
                continue;
            }
            String periodKey = firstJoin ? "join:first" : "join:" + System.currentTimeMillis();
            String claimKey = StoreKeys.claim(def.id(), player.getUniqueId(), periodKey);
            String transactionId = store.read(s -> "T" + s.nextSequence());
            boolean reserved = store.claimOnce(claimKey, () -> {});
            if (!reserved) {
                continue;
            }
            try {
                List<RewardEntry> payload = new ArrayList<>(def.fixedRewards().entries());
                payload.addAll(def.randomRewards().entries());
                rewards.deliver(player.getUniqueId(), player, def.id(), payload, transactionId, 0);
                store.recordAudit(new KitState.AuditRecord(
                        store.read(s -> "A" + s.nextSequence()), System.currentTimeMillis(),
                        player.getUniqueId(), def.id(), "join_claim",
                        "transaction=" + transactionId + ", first=" + firstJoin + ", world=" + worldName));
            } catch (RuntimeException exception) {
                store.mutate(s -> s.idempotencyKeys().remove(claimKey));
            }
        }
        if (firstJoin && inviterId != null) {
            bindFromJoin(player, inviterId);
        }
        store.mutate(s -> s.firstJoin().putIfAbsent(player.getUniqueId(), String.valueOf(System.currentTimeMillis())));
        return ClaimResult.success("join", "once", List.of());
    }

    private void bindFromJoin(Player newcomer, UUID inviterId) {
        ClaimResult bindable = checkBindable(inviterId, newcomer.getUniqueId());
        if (!bindable.success()) {
            return;
        }
        if (!completeBind(inviterId, newcomer.getUniqueId(), newcomer.getUniqueId(), "join_bind",
                "inviter=" + inviterId + ", newcomer=" + newcomer.getUniqueId())) {
            return;
        }
        grantInviteReward(inviterId, newcomer.getUniqueId());
    }

    public TimeWindow timeWindow(KitDefinition def) {
        if (!def.hasTimeLimit()) {
            return TimeWindow.always();
        }
        KitDefinition.LimitRule limit = def.limit();
        String mode = limit.mode().toLowerCase(Locale.ROOT);
        try {
            return switch (mode) {
                case "range" -> {
                    Long start = parseEpoch(limit.start());
                    Long end = parseEpoch(limit.end());
                    yield start == null || end == null ? TimeWindow.always()
                            : TimeWindow.range(start, end);
                }
                case "cron" -> TimeWindow.cron(limit.cron(), limit.durationSeconds(), limit.timeZone());
                default -> TimeWindow.always();
            };
        } catch (RuntimeException exception) {
            return TimeWindow.always();
        }
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

    public KitDefinition kit(String id) {
        return kits.apply(KitIds.normalize(id));
    }

    public KitDefinition findFirstOfType(KitType type) {
        for (KitDefinition def : config.get().kits().values()) {
            if (def.type() == type) {
                return def;
            }
        }
        return null;
    }

    private ClaimResult validateSelections(Player player, KitDefinition def,
                                           List<String> selections, String periodKey) {
        if (!def.choice().valid()) {
            return ClaimResult.failure(ClaimResult.Status.CONFIG_ERROR, "error.choice.config", def.id(), periodKey);
        }
        if (selections.size() < def.choice().minSelect() || selections.size() > def.choice().maxSelect()) {
            return ClaimResult.failure(ClaimResult.Status.INVALID_SELECTION, "error.choice.count", def.id(), periodKey);
        }
        Set<String> seen = new HashSet<>();
        Map<String, ChoiceOption> known = new LinkedHashMap<>();
        for (ChoiceOption option : def.choice().options()) {
            known.put(option.optionId(), option);
        }
        for (String raw : selections) {
            String id = KitIds.normalize(raw);
            ChoiceOption option = known.get(id);
            if (option == null) {
                return ClaimResult.failure(ClaimResult.Status.INVALID_SELECTION, "error.choice.unknown", def.id(), periodKey);
            }
            if (!seen.add(id)) {
                return ClaimResult.failure(ClaimResult.Status.INVALID_SELECTION, "error.choice.duplicate", def.id(), periodKey);
            }
            if (option.requiresPermission() && !player.hasPermission(option.permission())) {
                return ClaimResult.failure(ClaimResult.Status.PERMISSION_DENIED, "error.choice.permission", def.id(), periodKey);
            }
        }
        return ClaimResult.success(def.id(), periodKey, List.of());
    }

    private ClaimResult applyOpenCount(Player player, KitDefinition def, String periodKey,
                                       String transactionId, List<String> auditIds, boolean makeup) {
        String openKey = KitState.countKey("open", def.id(), player.getUniqueId());
        long opens = store.read(s -> {
            s.openCounts().merge(openKey, 1L, Long::sum);
            return s.openCounts().get(openKey);
        });
        for (OpenMilestone milestone : def.open().milestones()) {
            if (opens != milestone.count()) {
                continue;
            }
            String key = StoreKeys.milestone(def.id(), player.getUniqueId(), milestone.count());
            boolean reserved = store.claimOnce(key, () -> {});
            if (!reserved) {
                continue;
            }
            try {
                auditIds.addAll(rewards.deliver(player.getUniqueId(), player, def.id(),
                        milestone.rewards(), transactionId, 1));
            } catch (RuntimeException exception) {
                store.mutate(s -> {
                    s.idempotencyKeys().remove(key);
                    s.openCounts().computeIfPresent(openKey, (k, v) -> v > 0 ? v - 1 : 0L);
                });
            }
        }
        store.recordAudit(new KitState.AuditRecord(
                store.read(s -> "A" + s.nextSequence()), System.currentTimeMillis(),
                player.getUniqueId(), def.id(), makeup ? "claim_makeup" : "claim_success",
                "transaction=" + transactionId + ", period=" + periodKey + ", opens=" + opens));
        return ClaimResult.success(def.id(), periodKey, List.copyOf(auditIds));
    }

    private void rollback(String claimKey, String kitId, UUID playerId, String periodKey,
                          String transactionId) {
        store.mutate(s -> {
            s.idempotencyKeys().remove(claimKey);
            s.selections().remove(StoreKeys.selection(kitId, playerId, periodKey));
        });
        store.recordAudit(new KitState.AuditRecord(
                store.read(s -> "A" + s.nextSequence()), System.currentTimeMillis(),
                playerId, kitId, "claim_rollback",
                "transaction=" + transactionId + ", period=" + periodKey));
    }

    private String periodKey(KitDefinition def) {
        return PeriodKeys.currentFor(def.type(), def.cycle().timeZone());
    }

    private String previousKey(KitDefinition def) {
        return PeriodKeys.previousFor(def.type(), def.cycle().timeZone());
    }

    public Map<UUID, Long> invitedCounts() {
        return store.read(s -> {
            Map<UUID, Long> counts = new LinkedHashMap<>();
            for (List<UUID> inviters : s.inviteBinds().values()) {
                for (UUID inviter : inviters) {
                    counts.merge(inviter, 1L, Long::sum);
                }
            }
            return counts;
        });
    }

    public int cleanupExpired(int auditDays, int idempotencyDays) {
        if (auditDays <= 0 && idempotencyDays <= 0) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int[] removed = {0};
        store.mutate(s -> {
            if (auditDays > 0) {
                long cutoff = now - auditDays * 86_400_000L;
                int before = s.audits().size();
                s.audits().values().removeIf(record -> record.atMillis() > 0L && record.atMillis() < cutoff);
                removed[0] += before - s.audits().size();
            }
            if (idempotencyDays > 0) {
                String zone = config.get().timeZone();
                LocalDate cutoffDate = LocalDate.now(ZoneId.of(zone)).minusDays(idempotencyDays);
                Set<String> protectedPeriods = new HashSet<>();
                for (KitType type : List.of(KitType.DAILY, KitType.WEEKLY, KitType.MONTHLY)) {
                    protectedPeriods.add(PeriodKeys.currentFor(type, zone));
                    protectedPeriods.add(PeriodKeys.previousFor(type, zone));
                }
                int before = s.idempotencyKeys().size();
                s.idempotencyKeys().removeIf(key -> expiredPeriodKey(key, cutoffDate, protectedPeriods));
                removed[0] += before - s.idempotencyKeys().size();
            }
        });
        return removed[0];
    }

    private static boolean expiredPeriodKey(String key, LocalDate cutoffDate, Set<String> protectedPeriods) {
        boolean claimScoped = key.startsWith("claim|");
        boolean selectionScoped = key.startsWith("selection|");
        if (!claimScoped && !selectionScoped) {
            return false;
        }
        int lastBar = key.lastIndexOf('|');
        if (lastBar < 0) {
            return false;
        }
        String period = key.substring(lastBar + 1);
        if (protectedPeriods.contains(period)) {
            return false;
        }
        LocalDate start = periodStart(period);
        return start != null && start.isBefore(cutoffDate);
    }

    private static LocalDate periodStart(String period) {
        if (period.startsWith("d:") || period.startsWith("w:") || period.startsWith("m:")) {
            try {
                return LocalDate.parse(period.substring(2));
            } catch (DateTimeParseException invalid) {
                return null;
            }
        }
        return null;
    }

    private static boolean worldAllowed(KitDefinition def, String worldName) {
        List<String> worlds = def.join().worlds();
        return worlds.isEmpty() || worlds.contains(worldName);
    }
}
