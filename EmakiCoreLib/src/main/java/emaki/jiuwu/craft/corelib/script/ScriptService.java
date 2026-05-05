package emaki.jiuwu.craft.corelib.script;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ScriptService {

    ScriptExecutionResult execute(ScriptExecutionRequest request);

    default CompletableFuture<ScriptExecutionResult> executeAsync(ScriptExecutionRequest request) {
        return CompletableFuture.supplyAsync(() -> execute(request));
    }

    ScriptReloadResult reload();

    boolean enabled();

    List<String> loadedScripts();

    Optional<ScriptSource> findScript(String scriptPath);
}
