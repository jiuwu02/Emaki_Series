package emaki.jiuwu.craft.kits.apiimpl;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.kits.EmakiKitsPlugin;
import emaki.jiuwu.craft.kits.api.KitOperations;
import emaki.jiuwu.craft.kits.api.event.KitClaimedEvent;
import emaki.jiuwu.craft.kits.api.model.KitClaimView;
import emaki.jiuwu.craft.kits.config.KitIds;
import emaki.jiuwu.craft.kits.model.ClaimResult;
import emaki.jiuwu.craft.kits.model.KitDefinition;
import emaki.jiuwu.craft.kits.store.KitState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class DefaultKitsOperations implements KitOperations {

    private final EmakiKitsPlugin plugin;

    public DefaultKitsOperations(EmakiKitsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull EmakiResult<KitClaimView> claim(@Nullable Player player, @Nullable String kitId) {
        return claim(player, kitId, List.of());
    }

    @Override
    public @NotNull EmakiResult<KitClaimView> claim(@Nullable Player player, @Nullable String kitId,
                                                    @Nullable List<String> selections) {
        EmakiResult<KitClaimView> rejected = precheck(player, kitId);
        if (rejected != null) {
            return rejected;
        }
        List<String> safe = selections == null ? List.of() : List.copyOf(selections);
        ClaimResult result = plugin.kitService().claim(player, kitId, safe);
        EmakiResult<KitClaimView> mapped = map(result);
        fireIfSuccess(player, result, "claim");
        return mapped;
    }

    @Override
    public @NotNull EmakiResult<Unit> buy(@Nullable Player player, @Nullable String kitId) {
        EmakiResult<KitClaimView> rejected = precheck(player, kitId);
        if (rejected != null) {
            return toFailure(rejected);
        }
        ClaimResult result = plugin.kitService().buy(player, kitId);
        return mapUnit(result);
    }

    @Override
    public @NotNull EmakiResult<KitClaimView> redeem(@Nullable Player player, @Nullable String kitId,
                                                     @Nullable String code) {
        EmakiResult<KitClaimView> rejected = precheck(player, kitId);
        if (rejected != null) {
            return rejected;
        }
        if (code == null || code.isBlank()) {
            return EmakiResult.invalidInput("emakikits.api.blank_code");
        }
        ClaimResult result = plugin.kitService().redeem(player, kitId, code);
        EmakiResult<KitClaimView> mapped = map(result);
        fireIfSuccess(player, result, "cdk_redeem");
        return mapped;
    }

    @Override
    public @NotNull EmakiResult<Unit> grantPurchase(@Nullable Player player, @Nullable String kitId) {
        EmakiResult<KitClaimView> rejected = precheck(player, kitId);
        if (rejected != null) {
            return toFailure(rejected);
        }
        KitDefinition def = plugin.kit(KitIds.normalize(kitId));
        if (def == null) {
            return EmakiResult.notFound("emakikits.api.kit_not_found");
        }
        plugin.kitStore().mutate(s -> s.purchaseCounts().merge(
                KitState.countKey("purchase", def.id(), player.getUniqueId()), 1L, Long::sum));
        return EmakiResult.ok();
    }

    private EmakiResult<KitClaimView> precheck(Player player, String kitId) {
        if (player == null || !player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        if (kitId == null || kitId.isBlank()) {
            return EmakiResult.invalidInput("emakikits.api.blank_kit_id");
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.failure(FailureKind.WRONG_THREAD,
                    "emakikits.api.wrong_thread", Map.of());
        }
        return null;
    }

    private static EmakiResult<KitClaimView> map(ClaimResult result) {
        KitClaimView view = new KitClaimView(result.kitId(), result.periodKey(),
                result.success(), result.reasonKey(), result.auditIds());
        if (result.success()) {
            return EmakiResult.success(view);
        }
        return EmakiResult.failure(failureKind(result.status()), result.reasonKey(),
                Map.of("kit", result.kitId(), "period", result.periodKey()));
    }

    private static EmakiResult<Unit> mapUnit(ClaimResult result) {
        if (result.success()) {
            return EmakiResult.ok();
        }
        return EmakiResult.failure(failureKind(result.status()), result.reasonKey(),
                Map.of("kit", result.kitId()));
    }

    private static FailureKind failureKind(ClaimResult.Status status) {
        return switch (status) {
            case CONFIG_ERROR -> FailureKind.NOT_FOUND;
            case STORAGE_FAILED -> FailureKind.INTERNAL_ERROR;
            case PERMISSION_DENIED, CONDITION_FAILED, LIMIT_EXCEEDED, INVALID_SELECTION,
                 PAYMENT_FAILED, CDK_EXHAUSTED, ALREADY_CLAIMED -> FailureKind.REJECTED;
            default -> FailureKind.INTERNAL_ERROR;
        };
    }

    private static EmakiResult<Unit> toFailure(EmakiResult<KitClaimView> source) {
        if (source instanceof EmakiResult.Failure<KitClaimView> failure) {
            return EmakiResult.failure(failure.kind(), failure.reasonKey(), failure.placeholders());
        }
        return EmakiResult.unavailable();
    }

    private void fireIfSuccess(Player player, ClaimResult result, String action) {
        if (!result.success()) {
            return;
        }
        KitClaimView view = new KitClaimView(result.kitId(), result.periodKey(),
                true, result.reasonKey(), result.auditIds());
        Bukkit.getPluginManager().callEvent(new KitClaimedEvent(player, view, action));
    }
}
