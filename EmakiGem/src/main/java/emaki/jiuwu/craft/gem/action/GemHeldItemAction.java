package emaki.jiuwu.craft.gem.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.service.GemInlayService;
import emaki.jiuwu.craft.gem.service.GemUpgradeService;
import emaki.jiuwu.craft.gem.service.SocketOpenerService;

public final class GemHeldItemAction implements Action {

    enum Operation {
        OPEN_SOCKET,
        INLAY,
        EXTRACT,
        UPGRADE_GEM_ITEM,
        CLEAR_LAYER
    }

    private final EmakiGemPlugin plugin;
    private final String id;
    private final Operation operation;

    GemHeldItemAction(EmakiGemPlugin plugin, String id, Operation operation) {
        this.plugin = plugin;
        this.id = id;
        this.operation = operation;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Modify the action player's held gem item through EmakiGem services.";
    }

    @Override
    public String category() {
        return "emakigem";
    }

    @Override
    public List<ActionParameter> parameters() {
        return switch (operation) {
            case OPEN_SOCKET -> List.of(
                    ActionParameter.required("opener", ActionParameterType.STRING, "Socket opener id."),
                    ActionParameter.optional("slot", ActionParameterType.INTEGER, "-1", "Target socket slot."),
                    ActionParameter.optional("bypass", ActionParameterType.BOOLEAN, "false", "Bypass opener item requirement."));
            case INLAY -> List.of(
                    ActionParameter.required("slot", ActionParameterType.INTEGER, "Target socket slot."),
                    ActionParameter.optional("bypass_cost", ActionParameterType.BOOLEAN, "false", "Bypass configured cost."));
            case EXTRACT -> List.of(
                    ActionParameter.required("slot", ActionParameterType.INTEGER, "Target socket slot."),
                    ActionParameter.optional("bypass_cost", ActionParameterType.BOOLEAN, "false", "Bypass configured cost."));
            case UPGRADE_GEM_ITEM -> List.of(ActionParameter.optional("bypass_cost", ActionParameterType.BOOLEAN, "false", "Bypass configured cost."));
            case CLEAR_LAYER -> List.of();
        };
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "EmakiGem action requires a player context.");
        }
        return switch (operation) {
            case OPEN_SOCKET -> openSocket(player, arguments);
            case INLAY -> inlay(player, arguments);
            case EXTRACT -> extract(player, arguments);
            case UPGRADE_GEM_ITEM -> upgrade(player, arguments);
            case CLEAR_LAYER -> clearLayer(player);
        };
    }

    private ActionResult openSocket(Player player, Map<String, String> arguments) {
        ItemStack equipment = player.getInventory().getItemInMainHand();
        ItemStack opener = player.getInventory().getItemInOffHand();
        String openerId = value(arguments, "opener", "");
        int slot = intArgument(arguments, "slot", -1);
        SocketOpenerService.OpenResult result = plugin.socketOpenerService().openDirect(player, equipment, opener, openerId, slot, boolArgument(arguments, "bypass", false));
        if (!result.result().success()) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, result.result().messageKey());
        }
        player.getInventory().setItemInMainHand(result.updatedEquipment());
        player.getInventory().setItemInOffHand(result.updatedOpener());
        return ActionResult.ok(result.result().placeholders());
    }

    private ActionResult inlay(Player player, Map<String, String> arguments) {
        ItemStack equipment = player.getInventory().getItemInMainHand();
        ItemStack gem = player.getInventory().getItemInOffHand();
        GemInlayService.InlayResult result = plugin.inlayService().inlayDirect(player, equipment, gem, intArgument(arguments, "slot", -1), boolArgument(arguments, "bypass_cost", false), false);
        if (!result.result().success()) {
            if (result.result().inputConsumed()) {
                gem.subtract(1);
            }
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, result.result().messageKey());
        }
        player.getInventory().setItemInMainHand(result.updatedEquipment());
        gem.subtract(1);
        result.commit();
        return ActionResult.ok(result.result().placeholders());
    }

    private ActionResult extract(Player player, Map<String, String> arguments) {
        GemInlayService.ExtractDirectResult result = plugin.inlayService().extractDirect(player, player.getInventory().getItemInMainHand(), intArgument(arguments, "slot", -1), boolArgument(arguments, "bypass_cost", false));
        if (!result.result().success()) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, result.result().messageKey());
        }
        player.getInventory().setItemInMainHand(result.updatedEquipment());
        if (result.returnedGem() != null) {
            player.getInventory().addItem(result.returnedGem()).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        result.commit();
        return ActionResult.ok(result.result().placeholders());
    }

    private ActionResult upgrade(Player player, Map<String, String> arguments) {
        GemUpgradeService.Result result = plugin.upgradeService().upgradeGemItem(player, player.getInventory().getItemInMainHand(), boolArgument(arguments, "bypass_cost", false));
        if (!result.success()) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, result.messageKey());
        }
        return ActionResult.ok(result.placeholders());
    }

    private ActionResult clearLayer(Player player) {
        ItemStack updated = plugin.stateService().clearGemLayer(player.getInventory().getItemInMainHand());
        if (updated == null) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "EmakiGem action could not clear the gem layer.");
        }
        player.getInventory().setItemInMainHand(updated);
        return ActionResult.ok(Map.of("has_layer", plugin.stateService().hasStoredLayer(updated)));
    }

    private static String value(Map<String, String> arguments, String key, String fallback) {
        String value = arguments == null ? null : arguments.get(key);
        return Texts.isBlank(value) ? fallback : value;
    }

    private static int intArgument(Map<String, String> arguments, String key, int fallback) {
        return Numbers.tryParseInt(value(arguments, key, String.valueOf(fallback)), fallback);
    }

    private static boolean boolArgument(Map<String, String> arguments, String key, boolean fallback) {
        Boolean parsed = ActionParsers.parseBoolean(value(arguments, key, String.valueOf(fallback)));
        return parsed == null ? fallback : parsed;
    }
}
