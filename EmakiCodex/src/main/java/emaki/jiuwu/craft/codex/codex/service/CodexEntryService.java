package emaki.jiuwu.craft.codex.codex.service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementTrigger;
import emaki.jiuwu.craft.codex.codex.loader.CodexCategoryLoader;
import emaki.jiuwu.craft.codex.codex.model.CodexCategory;
import emaki.jiuwu.craft.codex.codex.model.CodexEntry;
import emaki.jiuwu.craft.codex.codex.model.CodexEntryState;
import emaki.jiuwu.craft.codex.codex.model.PlayerCodex;
import emaki.jiuwu.craft.codex.codex.provider.CodexProviderRegistrar;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class CodexEntryService {

    public enum EntryProgress {
        LOCKED,
        UNLOCKED,
        ACTIVATED,
        CLAIMED
    }

    private static final String CLAIM_ACTION_SOURCE = "codex.claim";

    private final EmakiCodexPlugin plugin;
    private final CodexCategoryLoader categoryLoader;
    private final PlayerCodexStore codexStore;
    private final CodexProviderRegistrar providerRegistrar;

    public CodexEntryService(EmakiCodexPlugin plugin,
            CodexCategoryLoader categoryLoader,
            PlayerCodexStore codexStore,
            CodexProviderRegistrar providerRegistrar) {
        this.plugin = plugin;
        this.categoryLoader = categoryLoader;
        this.codexStore = codexStore;
        this.providerRegistrar = providerRegistrar;
    }

    public EntryProgress progress(UUID playerId, String categoryId, String entryId) {
        PlayerCodex codex = codexStore.cached(playerId);
        CodexEntryState state = codex == null ? null : codex.state(categoryId, entryId);
        if (state == null) {
            return EntryProgress.LOCKED;
        }
        if (state.claimed()) {
            return EntryProgress.CLAIMED;
        }
        return state.activated() ? EntryProgress.ACTIVATED : EntryProgress.UNLOCKED;
    }

    public int unlockByTrigger(Player player, String triggerKey, Predicate<AdvancementTrigger> conditionPasses) {
        if (player == null || Texts.isBlank(triggerKey)) {
            return 0;
        }
        Set<String> pending = new LinkedHashSet<>();
        for (CodexCategory category : categoryLoader.all().values()) {
            for (CodexEntry entry : category.orderedEntries()) {
                for (AdvancementTrigger trigger : entry.triggers()) {
                    if (triggerKey.equals(trigger.event()) && conditionPasses.test(trigger)) {
                        pending.add(PlayerCodex.compositeKey(category.categoryId(), entry.entryId()));
                        break;
                    }
                }
            }
        }
        return unlockKeys(player, pending);
    }

    public int unlockByAdvancement(Player player, String advancementId) {
        if (player == null || Texts.isBlank(advancementId)) {
            return 0;
        }
        Set<String> pending = new LinkedHashSet<>();
        for (CodexCategory category : categoryLoader.all().values()) {
            for (CodexEntry entry : category.orderedEntries()) {
                for (String declared : entry.advancements()) {
                    if (advancementId.equalsIgnoreCase(declared)) {
                        pending.add(PlayerCodex.compositeKey(category.categoryId(), entry.entryId()));
                        break;
                    }
                }
            }
        }
        return unlockKeys(player, pending);
    }

    private int unlockKeys(Player player, Set<String> compositeKeys) {
        int unlocked = 0;
        for (String key : compositeKeys) {
            int separator = key.indexOf('/');
            if (separator <= 0) {
                continue;
            }
            if (unlock(player, key.substring(0, separator), key.substring(separator + 1)).isSuccess()) {
                unlocked++;
            }
        }
        return unlocked;
    }

    public EmakiResult<Unit> unlock(Player player, String categoryId, String entryId) {
        if (player == null) {
            return EmakiResult.targetOffline();
        }
        CodexEntry entry = categoryLoader.entryAt(categoryId, entryId);
        if (entry == null) {
            return EmakiResult.notFound("codex.entry.not_found");
        }
        UUID playerId = player.getUniqueId();
        long timestamp = System.currentTimeMillis();
        Boolean changed = codexStore.mutate(playerId, codexStore.currentGeneration(playerId),
                codex -> codex.unlock(categoryId, entryId, timestamp));
        if (changed == null) {
            return EmakiResult.rejected("codex.entry.state_stale");
        }
        if (!changed) {
            return EmakiResult.rejected("codex.entry.already_unlocked");
        }
        codexStore.saveAsync(playerId);
        plugin.messageService().send(player, "gui.entry_unlocked", Map.of(
                "codex_category", Texts.normalizeId(categoryId),
                "codex_entry", Texts.normalizeId(entryId),
                "codex_title", entry.title()));
        return EmakiResult.ok();
    }

    public EmakiResult<Unit> activate(Player player, String categoryId, String entryId) {
        if (player == null) {
            return EmakiResult.targetOffline();
        }
        if (categoryLoader.entryAt(categoryId, entryId) == null) {
            return EmakiResult.notFound("codex.entry.not_found");
        }
        UUID playerId = player.getUniqueId();
        PlayerCodex codex = codexStore.cached(playerId);
        if (codex == null || !codex.unlocked(categoryId, entryId)) {
            return EmakiResult.rejected("codex.entry.locked");
        }
        Boolean changed = codexStore.mutate(playerId, codexStore.currentGeneration(playerId),
                target -> target.activate(categoryId, entryId));
        if (changed == null) {
            return EmakiResult.rejected("codex.entry.state_stale");
        }
        if (!changed) {
            return EmakiResult.rejected("codex.entry.already_activated");
        }
        codexStore.saveAsync(playerId);
        providerRegistrar.resyncPlayer(player);
        return EmakiResult.ok();
    }

    public EmakiResult<Unit> claim(Player player, String categoryId, String entryId) {
        if (player == null) {
            return EmakiResult.targetOffline();
        }
        CodexEntry entry = categoryLoader.entryAt(categoryId, entryId);
        if (entry == null) {
            return EmakiResult.notFound("codex.entry.not_found");
        }
        UUID playerId = player.getUniqueId();
        PlayerCodex codex = codexStore.cached(playerId);
        if (codex == null || !codex.unlocked(categoryId, entryId)) {
            return EmakiResult.rejected("codex.entry.locked");
        }
        if (!entry.hasClaimActions()) {
            return EmakiResult.rejected("codex.entry.no_reward");
        }
        Boolean changed = codexStore.mutate(playerId, codexStore.currentGeneration(playerId),
                target -> target.claim(categoryId, entryId));
        if (changed == null) {
            return EmakiResult.rejected("codex.entry.state_stale");
        }
        if (!changed) {
            return EmakiResult.rejected("codex.entry.already_claimed");
        }
        codexStore.saveAsync(playerId);
        plugin.actionLines().run(entry.claimActions(), player, CLAIM_ACTION_SOURCE, false, Map.of(
                "codex_category", Texts.normalizeId(categoryId),
                "codex_entry", Texts.normalizeId(entryId),
                "codex_title", entry.title()), true);
        return EmakiResult.ok();
    }

    public EmakiResult<Integer> reset(Player player, String categoryId, String entryId) {
        if (player == null) {
            return EmakiResult.targetOffline();
        }
        UUID playerId = player.getUniqueId();
        long generation = codexStore.currentGeneration(playerId);
        Integer removed = Texts.isBlank(entryId)
                ? codexStore.mutate(playerId, generation, PlayerCodex::forgetAll)
                : codexStore.mutate(playerId, generation,
                        target -> target.forget(categoryId, entryId) ? 1 : 0);
        if (removed == null) {
            return EmakiResult.rejected("codex.entry.state_stale");
        }
        if (removed == 0) {
            return EmakiResult.rejected("codex.entry.nothing_to_reset");
        }
        codexStore.saveAsync(playerId);
        providerRegistrar.resyncPlayer(player);
        return EmakiResult.success(removed);
    }
}
