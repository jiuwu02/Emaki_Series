package emaki.jiuwu.craft.corelib.script;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ScriptService {

    ScriptExecutionResult execute(ScriptExecutionRequest request);

    ScriptExecutionResult invoke(ScriptInvocationRequest request);

    default CompletableFuture<ScriptExecutionResult> executeAsync(ScriptExecutionRequest request) {
        return CompletableFuture.supplyAsync(() -> execute(request));
    }

    default CompletableFuture<ScriptExecutionResult> invokeAsync(ScriptInvocationRequest request) {
        return CompletableFuture.supplyAsync(() -> invoke(request));
    }

    ScriptReloadResult reload();

    boolean enabled();

    List<String> loadedScripts();

    Optional<ScriptSource> findScript(String scriptPath);
}
