package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.Map;

public final class UseTemplateAction extends BaseAction {

    public static final String ID = "usetemplate";

    public UseTemplateAction() {
        super(ID, "template", "Expand a named action template.", ActionParameter.required("name", ActionParameterType.STRING, "Template name"));
    }

    @Override
    public boolean acceptsDynamicParameter(String name) {
        return Texts.lower(name).startsWith("with.");
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        return ActionResult.ok();
    }
}
