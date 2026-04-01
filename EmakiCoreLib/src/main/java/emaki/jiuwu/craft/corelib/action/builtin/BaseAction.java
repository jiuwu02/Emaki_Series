package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

abstract class BaseAction implements Action {

    private final String id;
    private final String category;
    private final String description;
    private final List<ActionParameter> parameters;

    BaseAction(String id, String category, String description, ActionParameter... parameters) {
        this.id = id;
        this.category = category;
        this.description = description;
        this.parameters = parameters == null ? List.of() : List.of(parameters);
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String category() {
        return category;
    }

    @Override
    public final String description() {
        return description;
    }

    @Override
    public final List<ActionParameter> parameters() {
        return parameters;
    }

    protected Player requirePlayer(ActionContext context) {
        return context == null ? null : context.player();
    }

    protected String stringArg(Map<String, String> arguments, String key) {
        return Texts.toStringSafe(arguments.get(key));
    }

    protected Map<String, String> applyDefaults(Map<String, String> arguments) {
        Map<String, String> copy = new LinkedHashMap<>(arguments);
        for (ActionParameter parameter : parameters) {
            if (Texts.isBlank(copy.get(parameter.name())) && Texts.isNotBlank(parameter.defaultValue())) {
                copy.put(parameter.name(), parameter.defaultValue());
            }
        }
        return copy;
    }

    protected ActionResult requirePlayerResult(ActionContext context) {
        return requirePlayer(context) == null
                ? ActionResult.failure(ActionErrorType.INVALID_STATE, "Action '" + id() + "' requires a player context.")
                : ActionResult.ok();
    }
}
