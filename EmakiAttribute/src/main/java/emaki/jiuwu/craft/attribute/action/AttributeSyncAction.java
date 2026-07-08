package emaki.jiuwu.craft.attribute.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class AttributeSyncAction implements Action {

    public static final String SYNC_ID = "attribute_sync";
    public static final String REFRESH_ID = "attribute_refresh";

    private final String id;
    private final String operationId;
    private final AttributeServiceFacade attributeService;

    AttributeSyncAction(String id, AttributeServiceFacade attributeService) {
        this(id, id, attributeService);
    }

    AttributeSyncAction(String id, String operationId, AttributeServiceFacade attributeService) {
        this.id = id;
        this.operationId = operationId;
        this.attributeService = attributeService;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return SYNC_ID.equals(operationId) ? "Synchronize the current player's attribute resources." : "Refresh attribute caches and synchronize players.";
    }

    @Override
    public String category() {
        return "attribute";
    }

    @Override
    public List<ActionParameter> parameters() {
        if (SYNC_ID.equals(operationId)) {
            return List.of(ActionParameter.optional("all", ActionParameterType.BOOLEAN, "false", "Synchronize all online players."));
        }
        return List.of(ActionParameter.optional("all", ActionParameterType.BOOLEAN, "true", "Refresh all online players instead of current player only."));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        boolean all = Boolean.parseBoolean(arguments.getOrDefault("all", REFRESH_ID.equals(operationId) ? "true" : "false"));
        if (REFRESH_ID.equals(operationId)) {
            attributeService.refreshCaches();
            if (all) {
                attributeService.resyncAllPlayers();
                return ActionResult.ok(Map.of("all", true));
            }
        } else if (all) {
            attributeService.resyncAllPlayers();
            return ActionResult.ok(Map.of("all", true));
        }
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Action '" + id + "' requires a player context when all=false.");
        }
        attributeService.syncPlayer(player, ResourceSyncReason.MANUAL, null);
        return ActionResult.ok(Map.of("all", false, "player", player.getName()));
    }
}
