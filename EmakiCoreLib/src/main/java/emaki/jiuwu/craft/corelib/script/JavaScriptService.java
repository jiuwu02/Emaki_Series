package emaki.jiuwu.craft.corelib.script;

public interface JavaScriptService extends ScriptService, AutoCloseable {

    default ScriptExecutionResult executeJavaScript(ScriptExecutionRequest request) {
        return execute(request);
    }

    default ScriptExecutionResult invokeJavaScript(ScriptInvocationRequest request) {
        return invoke(request);
    }

    @Override
    void close();
}
