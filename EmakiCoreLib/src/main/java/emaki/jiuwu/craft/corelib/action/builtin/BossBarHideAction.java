package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class BossBarHideAction extends BaseAction {

    public BossBarHideAction() {
        super(
                "bossbarhide",
                "feedback",
                "Hide a per-player boss bar by id.",
                ActionParameter.required("id", ActionParameterType.STRING, "Boss bar id or all")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        String id = stringArg(arguments, "id");
        if (Texts.isBlank(id)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "bossbarhide requires id.");
        }
        if ("all".equalsIgnoreCase(Texts.trim(id)) || "*".equals(Texts.trim(id))) {
            int removed = BuiltinBossBarRegistry.hideAll(context.player());
            return removed <= 0
                    ? ActionResult.skipped("No bossbars were active for player.")
                    : ActionResult.ok(Map.of("removed", removed));
        }
        boolean removed = BuiltinBossBarRegistry.hide(context.player(), id);
        return removed
                ? ActionResult.ok(Map.of("id", Texts.normalizeId(id)))
                : ActionResult.skipped("No bossbar found for id '" + id + "'.");
    }
}
