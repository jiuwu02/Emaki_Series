package emaki.jiuwu.craft.gem.apiimpl;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.GemOperations;
import emaki.jiuwu.craft.gem.api.model.GemExtractOutcome;
import emaki.jiuwu.craft.gem.api.model.GemInlayOutcome;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.service.GemExtractService;
import emaki.jiuwu.craft.gem.service.GemGuiMode;
import emaki.jiuwu.craft.gem.service.GemInlayService;
import emaki.jiuwu.craft.gem.service.SocketOpenerService;

/** Runtime-backed gem operations. */
public final class DefaultGemOperations implements GemOperations {

    private final EmakiGemPlugin plugin;

    public DefaultGemOperations(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public EmakiResult<GemInlayOutcome> inlay(Player actor,
            ItemStack equipment,
            ItemStack gemItem,
            int slotIndex) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        EmakiResult<GemInlayOutcome> validation = validatePlayerItemCall(actor, equipment, "gem.input.equipment_missing");
        if (validation != null) {
            return validation;
        }
        if (empty(gemItem)) {
            return EmakiResult.invalidInput("gem.input.gem_missing");
        }
        if (slotIndex < 0) {
            return EmakiResult.invalidInput("gem.input.slot_invalid");
        }
        GemItemInstance instance = plugin.itemMatcher().readGemInstance(gemItem);
        GemInlayService.InlayResult result =
                plugin.inlayService().inlayDirect(actor, equipment, gemItem, slotIndex, false, false);
        if (result == null || result.result() == null) {
            return EmakiResult.internalError("gem.inlay.result_missing");
        }
        String gemId = instance == null ? "" : Texts.lower(instance.gemId());
        int gemLevel = instance == null ? 1 : instance.level();
        if (!result.result().success()) {
            if (result.result().inputConsumed() && result.updatedEquipment() != null
                    && Texts.isNotBlank(result.operationId())) {
                GemInlayOutcome partialOutcome = new GemInlayOutcome(
                        result.operationId(),
                        result.updatedEquipment(),
                        true,
                        slotIndex,
                        gemId,
                        gemLevel);
                return EmakiResult.partial(partialOutcome, "gem.inlay.chance_failed");
            }
            return GemApiMapper.failure(result.result().messageKey(), result.result().placeholders());
        }
        if (result.updatedEquipment() == null || Texts.isBlank(result.operationId())) {
            return EmakiResult.internalError("gem.inlay.commit_missing");
        }
        result.commit();
        return EmakiResult.success(new GemInlayOutcome(
                result.operationId(),
                result.updatedEquipment(),
                result.result().inputConsumed(),
                slotIndex,
                gemId,
                gemLevel));
    }

    @Override
    public EmakiResult<GemExtractOutcome> extract(Player actor,
            ItemStack equipment,
            int slotIndex,
            boolean bypassCost) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        EmakiResult<GemExtractOutcome> validation = validatePlayerItemCall(actor, equipment, "gem.input.equipment_missing");
        if (validation != null) {
            return validation;
        }
        if (slotIndex < 0) {
            return EmakiResult.invalidInput("gem.input.slot_invalid");
        }
        GemItemInstance existing = GemApiMapper.readAssignment(plugin.stateService(), equipment, slotIndex);
        GemInlayService.ExtractDirectResult result =
                plugin.inlayService().extractDirect(actor, equipment, slotIndex, bypassCost);
        if (result == null || result.result() == null) {
            return EmakiResult.internalError("gem.extract.result_missing");
        }
        if (!result.result().success()) {
            return GemApiMapper.failure(result.result().messageKey(), result.result().placeholders());
        }
        if (result.updatedEquipment() == null || Texts.isBlank(result.operationId())) {
            return EmakiResult.internalError("gem.extract.commit_missing");
        }
        result.commit();
        String gemId = existing == null ? "" : Texts.lower(existing.gemId());
        int gemLevel = existing == null ? 1 : existing.level();
        GemDefinition definition = Texts.isBlank(gemId) ? null : plugin.gemLoader().get(gemId);
        String returnMode = definition == null || definition.extractReturn() == null
                ? ""
                : definition.extractReturn().mode();
        return EmakiResult.success(new GemExtractOutcome(
                result.operationId(),
                result.updatedEquipment(),
                result.returnedGem(),
                slotIndex,
                gemId,
                gemLevel,
                returnMode));
    }

    @Override
    public EmakiResult<ItemStack> openSocket(Player actor, ItemStack equipment, ItemStack openerItem) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        EmakiResult<ItemStack> validation = validatePlayerItemCall(actor, equipment, "gem.input.equipment_missing");
        if (validation != null) {
            return validation;
        }
        if (empty(openerItem)) {
            return EmakiResult.invalidInput("gem.input.opener_missing");
        }
        SocketOpenerService.OpenResult result = plugin.socketOpenerService().openDirect(actor, equipment, openerItem);
        if (result == null || result.result() == null) {
            return EmakiResult.internalError("gem.socket_open.result_missing");
        }
        if (!result.result().success()) {
            return GemApiMapper.failure(result.result().messageKey(), result.result().placeholders());
        }
        if (result.updatedEquipment() == null) {
            return EmakiResult.internalError("gem.socket_open.equipment_missing");
        }
        applyUpdatedStackAmount(openerItem, result.updatedOpener());
        return EmakiResult.success(result.updatedEquipment());
    }

    @Override
    public EmakiResult<ItemStack> createGemItem(String gemId, int level, int amount) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        if (Texts.isBlank(gemId)) {
            return EmakiResult.invalidInput("gem.input.gem_id_missing");
        }
        if (level < 1) {
            return EmakiResult.invalidInput("gem.input.level_invalid");
        }
        if (amount < 1) {
            return EmakiResult.invalidInput("gem.input.amount_invalid");
        }
        GemDefinition definition = plugin.gemLoader().get(Texts.lower(gemId));
        if (definition == null) {
            return EmakiResult.notFound("gem.definition_not_found");
        }
        ItemStack created = plugin.itemFactory().createGemItem(definition, level, amount);
        return created == null
                ? EmakiResult.internalError("gem.create.failed")
                : EmakiResult.success(created);
    }

    @Override
    public EmakiResult<ItemStack> clearGems(ItemStack equipment) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        if (empty(equipment)) {
            return EmakiResult.invalidInput("gem.input.equipment_missing");
        }
        if (!plugin.stateService().hasStoredLayer(equipment)) {
            return EmakiResult.notFound("gem.layer_not_found");
        }
        ItemStack cleared = plugin.stateService().clearGemLayer(equipment);
        return cleared == null
                ? EmakiResult.internalError("gem.clear.failed")
                : EmakiResult.success(cleared);
    }

    @Override
    public EmakiResult<Unit> openGui(Player player) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        EmakiResult<Unit> validation = validateOwnedPlayer(player);
        if (validation != null) {
            return validation;
        }
        return plugin.gemGuiService().open(player, GemGuiMode.INLAY)
                ? EmakiResult.ok()
                : EmakiResult.rejected("gem.gui.open_rejected");
    }

    @Override
    public EmakiResult<Unit> openSocketGui(Player player, ItemStack target) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        EmakiResult<Unit> validation = validateOwnedPlayer(player);
        if (validation != null) {
            return validation;
        }
        return plugin.gemGuiService().openSocket(player, target)
                ? EmakiResult.ok()
                : EmakiResult.rejected("gem.gui.socket_open_rejected");
    }

    private <T> EmakiResult<T> validatePlayerItemCall(Player player, ItemStack itemStack, String itemReason) {
        EmakiResult<T> playerValidation = validateOwnedPlayer(player);
        if (playerValidation != null) {
            return playerValidation;
        }
        return empty(itemStack) ? EmakiResult.invalidInput(itemReason) : null;
    }

    private <T> EmakiResult<T> validateOwnedPlayer(Player player) {
        if (player == null) {
            return EmakiResult.invalidInput("gem.input.player_missing");
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        if (!plugin.scheduling().ownsEntity(player)) {
            return EmakiResult.wrongThread();
        }
        return null;
    }

    private boolean ready() {
        return plugin != null
                && plugin.isEnabled()
                && plugin.publicApiReady()
                && plugin.scheduling() != null
                && plugin.gemLoader() != null
                && plugin.itemMatcher() != null
                && plugin.itemFactory() != null
                && plugin.stateService() != null
                && plugin.inlayService() != null
                && plugin.socketOpenerService() != null
                && plugin.gemGuiService() != null;
    }

    private static void applyUpdatedStackAmount(ItemStack original, ItemStack updated) {
        if (original == null) {
            return;
        }
        original.setAmount(updated == null ? 0 : updated.getAmount());
    }

    private static boolean empty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir();
    }
}
