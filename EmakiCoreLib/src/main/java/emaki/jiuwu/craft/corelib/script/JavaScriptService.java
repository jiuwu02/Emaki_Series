package emaki.jiuwu.craft.corelib.script;

public interface JavaScriptService extends ScriptService, AutoCloseable {

    default ScriptExecutionResult executeJavaScript(ScriptExecutionRequest request) {
        return execute(request);
    }

    @Override
    void close();
}
