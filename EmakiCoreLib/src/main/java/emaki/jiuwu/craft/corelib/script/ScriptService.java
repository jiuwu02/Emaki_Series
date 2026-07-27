package emaki.jiuwu.craft.corelib.script;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ScriptService {

    ScriptExecutionResult execute(ScriptExecutionRequest request);

    ScriptExecutionResult invoke(ScriptInvocationRequest request);

    default CompletableFuture<ScriptExecutionResult> executeAsync(ScriptExecutionRequest request) {
        try {
            return CompletableFuture.completedFuture(execute(request));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    default CompletableFuture<ScriptExecutionResult> invokeAsync(ScriptInvocationRequest request) {
        try {
            return CompletableFuture.completedFuture(invoke(request));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    ScriptReloadResult reload();

    boolean enabled();

    List<String> loadedScripts();

    Optional<ScriptSource> findScript(String scriptPath);
}
