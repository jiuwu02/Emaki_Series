package emaki.jiuwu.craft.corelib.script.graal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionRequest;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.ScriptModuleRegistry;
import emaki.jiuwu.craft.corelib.script.ScriptReloadResult;
import emaki.jiuwu.craft.corelib.script.ScriptRepository;
import emaki.jiuwu.craft.corelib.script.ScriptSource;
import emaki.jiuwu.craft.corelib.api.script.EmakiScriptApi;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class GraalJavaScriptService implements JavaScriptService {

    private final Plugin plugin;
    private final ScriptConfig config;
    private final ScriptRepository repository;
    private final java.util.function.Supplier<ActionExecutor> actionExecutorSupplier;
    private final ScriptModuleRegistry moduleRegistry;
    private final Map<String, ScriptSource> sourceCache = new ConcurrentHashMap<>();
    private final Engine engine;
    private boolean closed;

    public GraalJavaScriptService(Plugin plugin,
            ScriptConfig config,
            Path scriptRoot,
            java.util.function.Supplier<ActionExecutor> actionExecutorSupplier) {
        this(plugin, config, scriptRoot, actionExecutorSupplier, null);
    }

    public GraalJavaScriptService(Plugin plugin,
            ScriptConfig config,
            Path scriptRoot,
            java.util.function.Supplier<ActionExecutor> actionExecutorSupplier,
            ScriptModuleRegistry moduleRegistry) {
        this.plugin = plugin;
        this.config = config == null ? ScriptConfig.defaults() : config;
        this.repository = new ScriptRepository(scriptRoot, this.config.security());
        this.actionExecutorSupplier = actionExecutorSupplier;
        this.moduleRegistry = moduleRegistry == null ? new ScriptModuleRegistry() : moduleRegistry;
        this.engine = Engine.newBuilder()
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        reload();
    }

    @Override
    public ScriptExecutionResult execute(ScriptExecutionRequest request) {
        if (request == null) {
            return ScriptExecutionResult.failure("Script request cannot be null.");
        }
        return invoke(new ScriptInvocationRequest(
                request.sourcePlugin(),
                request.actionContext(),
                request.scriptPath(),
                request.functionName(),
                List.of(request.actionContext()),
                request.arguments(),
                request.timeoutMillis(),
                request.silent()
        ));
    }

    @Override
    public ScriptExecutionResult invoke(ScriptInvocationRequest request) {
        if (!enabled()) {
            return ScriptExecutionResult.failure("JavaScript scripting is disabled.");
        }
        if (request == null || Texts.isBlank(request.scriptPath())) {
            return ScriptExecutionResult.failure("Script path cannot be blank.");
        }
        Optional<ScriptSource> optionalSource = findScript(request.scriptPath());
        if (optionalSource.isEmpty()) {
            return ScriptExecutionResult.failure("Script not found: " + request.scriptPath());
        }
        ScriptSource source = optionalSource.get();
        String functionName = Texts.isBlank(request.functionName()) ? config.action().defaultFunction() : request.functionName();
        long start = System.nanoTime();
        try (Context context = createContext()) {
            EmakiScriptApi api = new EmakiScriptApi(
                    request.actionContext(),
                    request.namedArguments(),
                    actionExecutorSupplier == null ? null : actionExecutorSupplier.get(),
                    config,
                    source.logicalPath(),
                    request.sourcePlugin(),
                    moduleRegistry
            );
            context.getBindings("js").putMember("emaki", api);
            context.getBindings("js").putMember("args", request.namedArguments());
            context.eval(Source.newBuilder("js", source.content(), source.logicalPath()).buildLiteral());
            Value function = context.getBindings("js").getMember(functionName);
            if (function == null || !function.canExecute()) {
                return ScriptExecutionResult.failure("Function not found: " + functionName + " in " + source.logicalPath());
            }
            Value value = function.execute(request.arguments().toArray(Object[]::new));
            ScriptExecutionResult result = mapReturnValue(value);
            if (config.debug().logScriptExecute()) {
                log("Executed script " + source.logicalPath() + "#" + functionName + " in " + ((System.nanoTime() - start) / 1_000_000D) + " ms.");
            }
            return result;
        } catch (Exception exception) {
            if (!request.silent()) {
                warn("Script execution failed: " + source.logicalPath() + "#" + functionName + " - " + exception.getMessage());
                if (config.debug().printStacktrace()) {
                    exception.printStackTrace();
                }
            }
            return ScriptExecutionResult.failure("Script execution failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public ScriptReloadResult reload() {
        try {
            repository.ensureDirectories(config.paths().createDirectories());
            repository.releaseDefaultScripts(plugin);
            sourceCache.clear();
            List<String> scripts = repository.scan();
            return ScriptReloadResult.success(scripts);
        } catch (IOException | RuntimeException exception) {
            warn("Failed to reload script repository: " + exception.getMessage());
            return ScriptReloadResult.failure(exception.getMessage());
        }
    }

    @Override
    public boolean enabled() {
        return !closed && config.enabled();
    }

    @Override
    public List<String> loadedScripts() {
        try {
            return repository.scan();
        } catch (IOException exception) {
            return List.of();
        }
    }

    @Override
    public Optional<ScriptSource> findScript(String scriptPath) {
        if (!config.engine().cacheEnabled()) {
            return repository.find(scriptPath);
        }
        Optional<ScriptSource> fresh = repository.find(scriptPath);
        if (fresh.isEmpty()) {
            return Optional.empty();
        }
        ScriptSource source = fresh.get();
        ScriptSource cached = sourceCache.get(source.logicalPath());
        if (cached != null && cached.sha256().equals(source.sha256())) {
            return Optional.of(cached);
        }
        sourceCache.put(source.logicalPath(), source);
        return Optional.of(source);
    }

    @Override
    public void close() {
        closed = true;
        sourceCache.clear();
        engine.close();
    }

    private Context createContext() {
        ScriptConfig.Engine engineConfig = config.engine();
        Context.Builder builder = Context.newBuilder("js")
                .engine(engine)
                .allowExperimentalOptions(true)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> engineConfig.allowHostClassLookup())
                .allowCreateThread(engineConfig.allowThreads())
                .allowNativeAccess(engineConfig.allowNativeAccess())
                .allowIO(engineConfig.allowIo());
        return builder.build();
    }

    private ScriptExecutionResult mapReturnValue(Value value) {
        if (value == null || value.isNull()) {
            return ScriptExecutionResult.success(null, "");
        }
        if (value.isBoolean()) {
            return value.asBoolean()
                    ? ScriptExecutionResult.success(true, "")
                    : ScriptExecutionResult.failure("Script returned false.");
        }
        if (value.isString()) {
            return ScriptExecutionResult.success(value.asString(), value.asString());
        }
        if (value.hasMembers()) {
            boolean success = !value.hasMember("success") || asBoolean(value.getMember("success"), true);
            boolean skipped = value.hasMember("skipped") && asBoolean(value.getMember("skipped"), false);
            String message = value.hasMember("message") ? Texts.toStringSafe(detachValue(value.getMember("message"))) : "";
            Map<String, Object> output = new LinkedHashMap<>();
            if (value.hasMember("output") && value.getMember("output").hasMembers()) {
                Value rawOutput = value.getMember("output");
                for (String key : rawOutput.getMemberKeys()) {
                    output.put(key, detachValue(rawOutput.getMember(key)));
                }
            }
            if (skipped) {
                return ScriptExecutionResult.skipped(message);
            }
            return success
                    ? ScriptExecutionResult.success(detachValue(value), message, output)
                    : ScriptExecutionResult.failure(Texts.isBlank(message) ? "Script returned failure." : message);
        }
        return ScriptExecutionResult.success(detachValue(value), "");
    }

    private Object detachValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (value.hasArrayElements()) {
            List<Object> result = new ArrayList<>();
            long size = value.getArraySize();
            for (long index = 0; index < size; index++) {
                result.add(detachValue(value.getArrayElement(index)));
            }
            return Collections.unmodifiableList(result);
        }
        if (value.hasMembers()) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                result.put(key, detachValue(value.getMember(key)));
            }
            return Collections.unmodifiableMap(result);
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.as(Object.class);
        }
        return Texts.toStringSafe(value.as(Object.class));
    }

    private boolean asBoolean(Value value, boolean fallback) {
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            return Boolean.parseBoolean(value.asString());
        }
        return fallback;
    }

    private void log(String message) {
        if (plugin != null) {
            plugin.getLogger().info(message);
        }
    }

    private void warn(String message) {
        if (plugin != null) {
            plugin.getLogger().warning(message);
        }
    }
}
