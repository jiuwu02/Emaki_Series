package emaki.jiuwu.craft.corelib.execution;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.Server;

import emaki.jiuwu.craft.corelib.platform.paper.execution.PaperExecutionBackend;

public final class ExecutionBackendLoader {

    private static final String FOLIA_BACKEND_CLASS =
            "emaki.jiuwu.craft.corelib.platform.folia.execution.FoliaExecutionBackend";

    private ExecutionBackendLoader() {
    }

    public static LoadedExecution load(Server server, PlatformCapabilities capabilities) {
        if (server == null) {
            throw new IllegalStateException("Server is unavailable; execution backend cannot be initialized");
        }
        PlatformCapabilities detected = capabilities == null ? PlatformCapabilities.detect(server) : capabilities;
        ExecutionBackend backend = detected.folia()
                ? loadFoliaBackend(server, detected)
                : new PaperExecutionBackend(server);
        return new LoadedExecution(backend, backend);
    }

    private static ExecutionBackend loadFoliaBackend(Server server, PlatformCapabilities capabilities) {
        if (!capabilities.foliaBackendReady()) {
            throw new IllegalStateException("Folia was detected but required scheduler or ownership capabilities are unavailable");
        }
        try {
            Class<?> backendClass = Class.forName(
                    FOLIA_BACKEND_CLASS,
                    true,
                    ExecutionBackendLoader.class.getClassLoader());
            Object backend = backendClass.getConstructor(Server.class).newInstance(server);
            if (!(backend instanceof ExecutionBackend executionBackend)) {
                throw new IllegalStateException("Folia execution backend does not implement the CoreLib execution contract");
            }
            return executionBackend;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("Failed to initialize the Folia execution backend", cause);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Failed to link the Folia execution backend", exception);
        }
    }

    public record LoadedExecution(ExecutionDispatcher dispatcher, ThreadOwnership ownership) {
    }
}
