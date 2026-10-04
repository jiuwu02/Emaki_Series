package emaki.jiuwu.craft.corelib.execution;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.Server;

import emaki.jiuwu.craft.corelib.platform.paper.execution.PaperExecutionBackend;
import emaki.jiuwu.craft.corelib.runtime.CapabilityProbe;

public final class ExecutionBackendLoader {

    private static final String FOLIA_BACKEND_CLASS =
            "emaki.jiuwu.craft.corelib.platform.folia.execution.FoliaExecutionBackend";

    private ExecutionBackendLoader() {
    }

    public static LoadedExecution load(Server server, CapabilityProbe capabilities) {
        if (server == null) {
            throw new IllegalStateException("服务器不可用；无法初始化执行后端");
        }
        CapabilityProbe detected = capabilities == null ? CapabilityProbe.detect(server) : capabilities;
        ExecutionBackend backend = detected.folia()
                ? loadFoliaBackend(server, detected)
                : new PaperExecutionBackend(server);
        return new LoadedExecution(backend, backend);
    }

    private static ExecutionBackend loadFoliaBackend(Server server, CapabilityProbe capabilities) {
        if (!capabilities.foliaBackendReady()) {
            throw new IllegalStateException("检测到 Folia，但所需的调度器或归属能力不可用");
        }
        try {
            Class<?> backendClass = Class.forName(
                    FOLIA_BACKEND_CLASS,
                    true,
                    ExecutionBackendLoader.class.getClassLoader());
            Object backend = backendClass.getConstructor(Server.class).newInstance(server);
            if (!(backend instanceof ExecutionBackend executionBackend)) {
                throw new IllegalStateException("Folia 执行后端未实现 CoreLib 执行契约");
            }
            return executionBackend;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("初始化 Folia 执行后端失败", cause);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("链接 Folia 执行后端失败", exception);
        }
    }

    public record LoadedExecution(ExecutionDispatcher dispatcher, ThreadOwnership ownership) {
    }
}
