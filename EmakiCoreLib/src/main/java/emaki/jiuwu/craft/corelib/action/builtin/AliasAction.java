package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutionMode;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

/** Delegates an additional action id to an existing action implementation. */
public final class AliasAction implements Action {

    private final String id;
    private final Action delegate;

    public AliasAction(String id, Action delegate) {
        this.id = Texts.normalizeId(id);
        this.delegate = delegate;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return delegate.description();
    }

    @Override
    public String category() {
        return delegate.category();
    }

    @Override
    public String version() {
        return delegate.version();
    }

    @Override
    public List<ActionParameter> parameters() {
        return delegate.parameters();
    }

    @Override
    public boolean acceptsDynamicParameter(String name) {
        return delegate.acceptsDynamicParameter(name);
    }

    @Override
    public ActionExecutionMode executionMode() {
        return delegate.executionMode();
    }

    @Override
    public long timeoutMillis() {
        return delegate.timeoutMillis();
    }

    @Override
    public ActionResult validate(Map<String, String> arguments) {
        return delegate.validate(arguments);
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        return delegate.execute(context, arguments);
    }
}
