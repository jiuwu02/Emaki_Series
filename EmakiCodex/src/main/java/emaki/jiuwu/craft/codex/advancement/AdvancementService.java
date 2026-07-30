package emaki.jiuwu.craft.codex.advancement;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;
import emaki.jiuwu.craft.codex.api.event.AdvancementGrantEvent;
import emaki.jiuwu.craft.codex.api.event.AdvancementRevokeEvent;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.text.Texts;

/** Central mutation path for every EmakiCodex grant and revoke. */
public final class AdvancementService {

    private final EmakiCodexPlugin plugin;
    private final AdvancementRegistrar registrar;

    public AdvancementService(EmakiCodexPlugin plugin, AdvancementRegistrar registrar) {
        this.plugin = plugin;
        this.registrar = registrar;
    }

    public EmakiResult<Unit> grant(UUID playerId, String advancementId) {
        return playerId == null
                ? EmakiResult.invalidInput("codex.player.required")
                : grant(resolvePlayer(playerId), advancementId);
    }

    public EmakiResult<Unit> grant(Player player, String advancementId) {
        Resolved resolved = resolve(player, advancementId);
        if (resolved.failure() != null) {
            return resolved.failure();
        }
        AdvancementProgress progress = player.getAdvancementProgress(resolved.advancement());
        if (progress.isDone()) {
            return EmakiResult.rejected("codex.grant.already_completed");
        }
        AdvancementGrantEvent event = new AdvancementGrantEvent(
                player, advancementId.trim(), resolved.key().toString());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return EmakiResult.failure(FailureKind.CANCELLED, "codex.grant.cancelled");
        }
        return progress.awardCriteria(AdvancementDefinition.CRITERION)
                ? EmakiResult.ok()
                : EmakiResult.rejected("codex.grant.rejected");
    }

    public EmakiResult<Unit> revoke(UUID playerId, String advancementId) {
        return playerId == null
                ? EmakiResult.invalidInput("codex.player.required")
                : revoke(resolvePlayer(playerId), advancementId);
    }

    public EmakiResult<Unit> revoke(Player player, String advancementId) {
        Resolved resolved = resolve(player, advancementId);
        if (resolved.failure() != null) {
            return resolved.failure();
        }
        AdvancementProgress progress = player.getAdvancementProgress(resolved.advancement());
        if (!progress.isDone()) {
            return EmakiResult.rejected("codex.revoke.not_completed");
        }
        AdvancementRevokeEvent event = new AdvancementRevokeEvent(
                player, advancementId.trim(), resolved.key().toString());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return EmakiResult.failure(FailureKind.CANCELLED, "codex.revoke.cancelled");
        }
        return progress.revokeCriteria(AdvancementDefinition.CRITERION)
                ? EmakiResult.ok()
                : EmakiResult.rejected("codex.revoke.rejected");
    }

    private Resolved resolve(Player player, String advancementId) {
        if (!plugin.isEnabled() || registrar == null) {
            return Resolved.failed(EmakiResult.unavailable());
        }
        if (plugin.appConfig() == null || !plugin.appConfig().advancementEnabled()) {
            return Resolved.failed(EmakiResult.rejected("codex.advancement.disabled"));
        }
        if (player == null || !player.isOnline()) {
            return Resolved.failed(EmakiResult.targetOffline());
        }
        if (Texts.isBlank(advancementId)) {
            return Resolved.failed(EmakiResult.invalidInput("codex.advancement.id_required"));
        }
        if (plugin.threadOwnership() == null || !plugin.threadOwnership().isEntityOwned(player)) {
            return Resolved.failed(EmakiResult.wrongThread());
        }
        NamespacedKey key = registrar.resolveKey(advancementId);
        if (key == null) {
            return Resolved.failed(EmakiResult.notFound("codex.advancement.not_found"));
        }
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            return Resolved.failed(EmakiResult.notFound("codex.advancement.missing_on_server"));
        }
        return new Resolved(key, advancement, null);
    }

    private static Player resolvePlayer(UUID playerId) {
        return playerId == null ? null : Bukkit.getPlayer(playerId);
    }

    private record Resolved(NamespacedKey key,
                            Advancement advancement,
                            EmakiResult<Unit> failure) {
        private static Resolved failed(EmakiResult<Unit> failure) {
            return new Resolved(null, null, failure);
        }
    }
}
