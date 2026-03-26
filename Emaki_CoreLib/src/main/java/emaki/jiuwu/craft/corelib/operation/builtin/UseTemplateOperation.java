package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.Map;

public final class UseTemplateOperation extends BaseOperation {

    public UseTemplateOperation() {
        super("use_template", "template", "Expand a named operation template.", OperationParameter.required("name", OperationParameterType.STRING, "Template name"));
    }

    @Override
    public boolean acceptsDynamicParameter(String name) {
        return Texts.lower(name).startsWith("with.");
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        return OperationResult.ok();
    }
}
