package emaki.jiuwu.craft.corelib.script.graal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.api.script.EmakiScriptApi;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptDeferredOperationQueue;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionRequest;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptHostObjectProxy;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.ScriptModuleRegistry;
import emaki.jiuwu.craft.corelib.script.ScriptReloadResult;
import emaki.jiuwu.craft.corelib.script.ScriptRepository;
import emaki.jiuwu.craft.corelib.script.ScriptSource;
import emaki.jiuwu.craft.corelib.script.ScriptWorkerBoundary;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class GraalJavaScriptService implements JavaScriptService {

    private static final int WORKER_COUNT = 1;
    private static final int WORKER_QUEUE_CAPACITY = 64;
    private static final long RESULT_WAIT_GRACE_MILLIS = 500L;
    private static final long MAX_CLOSE_DRAIN_MILLIS = 5_000L;

    private final Plugin plugin;
    private final ScriptConfig config;
    private final ScriptRepository repository;
    private final java.util.function.Supplier<ActionExecutor> actionExecutorSupplier;
    private final ScriptModuleRegistry moduleRegistry;
    private final boolean releaseDefaultScripts;
    private final Map<String, ScriptSource> sourceCache = new ConcurrentHashMap<>();
    private final Engine engine;
    private final ThreadPoolExecutor workerExecutor;
    private final ScheduledThreadPoolExecutor watchdogExecutor;
    private final Map<Long, Invocation> activeInvocations = new ConcurrentHashMap<>();
    private final AtomicLong invocationSequence = new AtomicLong();
    private final AtomicReference<ServiceState> state = new AtomicReference<>(ServiceState.ACTIVE);
    private final CountDownLatch closeCompleted = new CountDownLatch(1);

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
        this(plugin, config, scriptRoot, actionExecutorSupplier, moduleRegistry, true);
    }

    public GraalJavaScriptService(Plugin plugin,
            ScriptConfig config,
            Path scriptRoot,
            java.util.function.Supplier<ActionExecutor> actionExecutorSupplier,
            ScriptModuleRegistry moduleRegistry,
            boolean releaseDefaultScripts) {
        this.plugin = plugin;
        this.config = config == null ? ScriptConfig.defaults() : config;
        this.repository = new ScriptRepository(scriptRoot, this.config.security());
        this.actionExecutorSupplier = actionExecutorSupplier;
        this.moduleRegistry = moduleRegistry == null ? new ScriptModuleRegistry() : moduleRegistry;
        this.releaseDefaultScripts = releaseDefaultScripts;
        this.engine = Engine.newBuilder()
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        this.workerExecutor = new ThreadPoolExecutor(
                WORKER_COUNT,
                WORKER_COUNT,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(WORKER_QUEUE_CAPACITY),
                threadFactory("emaki-graal-worker"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.watchdogExecutor = new ScheduledThreadPoolExecutor(1, threadFactory("emaki-graal-watchdog"));
        this.watchdogExecutor.setRemoveOnCancelPolicy(true);
        reload();
    }

    @Override
    public ScriptExecutionResult execute(ScriptExecutionRequest request) {
        if (request == null) {
            return ScriptExecutionResult.failure("Script request cannot be null.");
        }
        return invoke(toInvocationRequest(request));
    }

    @Override
    public CompletableFuture<ScriptExecutionResult> executeAsync(ScriptExecutionRequest request) {
        if (request == null) {
            return CompletableFuture.completedFuture(ScriptExecutionResult.failure("Script request cannot be null."));
        }
        return invokeAsync(toInvocationRequest(request));
    }

    @Override
    public ScriptExecutionResult invoke(ScriptInvocationRequest request) {
        return submit(request).awaitResult();
    }

    @Override
    public CompletableFuture<ScriptExecutionResult> invokeAsync(ScriptInvocationRequest request) {
        return submit(request).future();
    }

    @Override
    public ScriptReloadResult reload() {
        if (state.get() != ServiceState.ACTIVE) {
            return ScriptReloadResult.failure("JavaScript scripting is disabled.");
        }
        try {
            repository.ensureDirectories(config.paths().createDirectories());
            if (releaseDefaultScripts) {
                repository.releaseDefaultScripts(plugin);
            }
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
        return state.get() == ServiceState.ACTIVE && config.runtimeEnabled();
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
        ServiceState previous = state.getAndUpdate(current -> current == ServiceState.ACTIVE ? ServiceState.CLOSING : current);
        if (previous == ServiceState.CLOSED) {
            return;
        }
        if (previous == ServiceState.CLOSING) {
            awaitCloseCompletion();
            return;
        }
        try {
            sourceCache.clear();
            for (Invocation invocation : List.copyOf(activeInvocations.values())) {
                invocation.requestCancellation(CancellationReason.DISABLED);
            }
            List<Runnable> queued = workerExecutor.shutdownNow();
            for (Runnable runnable : queued) {
                if (runnable instanceof Invocation invocation) {
                    invocation.abortBeforeStart(CancellationReason.DISABLED);
                }
            }
            watchdogExecutor.shutdownNow();
            closeEngine(true);
            awaitWorkerDrain();
        } finally {
            state.set(ServiceState.CLOSED);
            closeCompleted.countDown();
        }
    }

    int activeInvocationCount() {
        return activeInvocations.size();
    }

    int runningInvocationCount() {
        return (int) activeInvocations.values().stream().filter(invocation -> invocation.guestRunning.get()).count();
    }

    private Submission submit(ScriptInvocationRequest request) {
        if (!enabled()) {
            return Submission.immediate(disabledResult());
        }
        if (request == null || Texts.isBlank(request.scriptPath())) {
            return Submission.immediate(ScriptExecutionResult.failure("Script path cannot be blank."));
        }
        Optional<ScriptSource> optionalSource = findScript(request.scriptPath());
        if (optionalSource.isEmpty()) {
            return Submission.immediate(ScriptExecutionResult.failure("Script not found: " + request.scriptPath()));
        }
        try {
            PreparedInvocation prepared = prepare(request, optionalSource.get());
            Invocation invocation = new Invocation(invocationSequence.incrementAndGet(), prepared, request.silent());
            activeInvocations.put(invocation.id, invocation);
            if (state.get() != ServiceState.ACTIVE) {
                invocation.abortBeforeStart(CancellationReason.DISABLED);
                return Submission.async(invocation);
            }
            try {
                workerExecutor.execute(invocation);
            } catch (RejectedExecutionException exception) {
                activeInvocations.remove(invocation.id, invocation);
                ScriptExecutionResult result = state.get() == ServiceState.ACTIVE
                        ? ScriptExecutionResult.failure("JavaScript invocation rejected: worker queue is full.", exception)
                        : disabledResult();
                invocation.complete(result);
            }
            return Submission.async(invocation);
        } catch (RuntimeException exception) {
            if (!request.silent()) {
                warn("Script invocation snapshot failed: " + exception.getMessage());
            }
            return Submission.immediate(ScriptExecutionResult.failure(
                    "Script invocation snapshot failed: " + exceptionMessage(exception),
                    exception
            ));
        }
    }

    private PreparedInvocation prepare(ScriptInvocationRequest request, ScriptSource source) {
        String functionName = Texts.isBlank(request.functionName())
                ? config.action().defaultFunction()
                : request.functionName();
        long timeoutMillis = config.clampTimeoutMillis(request.timeoutMillis());
        @SuppressWarnings("unchecked")
        List<Object> arguments = (List<Object>) ScriptHostObjectProxy.wrapIfExported(request.arguments());
        @SuppressWarnings("unchecked")
        Map<String, Object> namedArguments = (Map<String, Object>) ScriptHostObjectProxy.wrapIfExported(request.namedArguments());
        @SuppressWarnings("unchecked")
        Map<String, Object> moduleOverrides = (Map<String, Object>) ScriptHostObjectProxy.wrapIfExported(request.moduleOverrides());
        ActionExecutor actionExecutor = actionExecutorSupplier == null ? null : actionExecutorSupplier.get();
        ScriptDeferredOperationQueue deferredOperations = new ScriptDeferredOperationQueue(
                plugin,
                actionExecutor,
                request.actionContext()
        );
        EmakiScriptApi api = new EmakiScriptApi(
                request.actionContext(),
                namedArguments,
                actionExecutor,
                config,
                source.logicalPath(),
                request.sourcePlugin(),
                moduleRegistry,
                moduleOverrides,
                deferredOperations
        );
        Object apiBinding = ScriptHostObjectProxy.wrapIfExported(api);
        return new PreparedInvocation(
                source,
                functionName,
                arguments,
                namedArguments,
                apiBinding,
                timeoutMillis,
                deferredOperations
        );
    }

    private ScriptInvocationRequest toInvocationRequest(ScriptExecutionRequest request) {
        return new ScriptInvocationRequest(
                request.sourcePlugin(),
                request.actionContext(),
                request.scriptPath(),
                request.functionName(),
                Collections.singletonList(request.actionContext()),
                request.arguments(),
                request.timeoutMillis(),
                request.silent()
        );
    }

    private Context createContext() {
        return Context.newBuilder("js")
                .engine(engine)
                .allowExperimentalOptions(true)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowHostAccess(createHostAccess())
                .allowHostClassLookup(_ -> false)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .allowIO(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .build();
    }

    private HostAccess createHostAccess() {
        return HostAccess.newBuilder(HostAccess.EXPLICIT)
                .allowArrayAccess(true)
                .allowListAccess(true)
                .allowMapAccess(true)
                .allowIterableAccess(true)
                .allowIteratorAccess(true)
                .build();
    }

    private ScriptExecutionResult executePrepared(Invocation invocation, Context context) throws IOException {
        PreparedInvocation prepared = invocation.prepared;
        long startedAt = System.nanoTime();
        context.getBindings("js").putMember("emaki", prepared.apiBinding());
        context.getBindings("js").putMember("args", prepared.namedArguments());
        context.eval(Source.newBuilder("js", prepared.source().content(), prepared.source().logicalPath()).buildLiteral());
        Value function = context.getBindings("js").getMember(prepared.functionName());
        if (function == null || !function.canExecute()) {
            return ScriptExecutionResult.failure(
                    "Function not found: " + prepared.functionName() + " in " + prepared.source().logicalPath()
            );
        }
        Value value = function.execute(prepared.arguments().toArray(Object[]::new));
        ScriptExecutionResult result = mapReturnValue(value);
        if (config.debug().logScriptExecute()) {
            log("Executed script " + prepared.source().logicalPath() + "#" + prepared.functionName()
                    + " in " + ((System.nanoTime() - startedAt) / 1_000_000D) + " ms.");
        }
        return result;
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
            return ScriptHostObjectProxy.snapshotValue(value.asHostObject());
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
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }
        return Texts.toStringSafe(value);
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

    private void awaitWorkerDrain() {
        long configured = Math.max(250L, config.engine().maxTimeoutMillis() + 250L);
        long drainMillis = Math.min(MAX_CLOSE_DRAIN_MILLIS, configured);
        try {
            workerExecutor.awaitTermination(drainMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitCloseCompletion() {
        try {
            closeCompleted.await(MAX_CLOSE_DRAIN_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeEngine(boolean cancelIfExecuting) {
        try {
            engine.close(cancelIfExecuting);
        } catch (RuntimeException ignored) {
        }
    }

    private ThreadFactory threadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private ScriptExecutionResult cancellationResult(CancellationReason reason, long timeoutMillis) {
        return switch (reason) {
            case TIMEOUT -> ScriptExecutionResult.failure(
                    "JavaScript invocation timed out after " + timeoutMillis + " ms.",
                    new TimeoutException("JavaScript invocation timed out after " + timeoutMillis + " ms.")
            );
            case DISABLED -> disabledResult();
            case INTERRUPTED -> ScriptExecutionResult.failure(
                    "JavaScript invocation was interrupted.",
                    new InterruptedException("JavaScript invocation was interrupted.")
            );
            case NONE -> ScriptExecutionResult.failure("Script execution was cancelled.", new CancellationException("cancelled"));
        };
    }

    private ScriptExecutionResult disabledResult() {
        return ScriptExecutionResult.failure(
                "JavaScript scripting is disabled.",
                new CancellationException("JavaScript scripting is disabled.")
        );
    }

    private String exceptionMessage(Throwable throwable) {
        String message = throwable == null ? "" : throwable.getMessage();
        return Texts.isBlank(message) && throwable != null ? throwable.getClass().getSimpleName() : Texts.toStringSafe(message);
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

    private enum ServiceState {
        ACTIVE,
        CLOSING,
        CLOSED
    }

    private enum CancellationReason {
        NONE,
        TIMEOUT,
        DISABLED,
        INTERRUPTED
    }

    private record PreparedInvocation(ScriptSource source,
            String functionName,
            List<Object> arguments,
            Map<String, Object> namedArguments,
            Object apiBinding,
            long timeoutMillis,
            ScriptDeferredOperationQueue deferredOperations) {
    }

    private record Submission(ScriptExecutionResult immediate, Invocation invocation) {

        private static Submission immediate(ScriptExecutionResult result) {
            return new Submission(result, null);
        }

        private static Submission async(Invocation invocation) {
            return new Submission(null, invocation);
        }

        private ScriptExecutionResult awaitResult() {
            if (immediate != null) {
                return immediate;
            }
            ScriptExecutionResult result = invocation.awaitResult();
            ScriptDeferredOperationQueue deferred = invocation.prepared.deferredOperations();
            if (deferred == null || deferred.isEmpty()) {
                return result;
            }
            deferred.discard();
            return ScriptExecutionResult.failure(
                    "Script queued side effects during a synchronous invocation; use invokeAsync/executeAsync instead."
            );
        }

        private CompletableFuture<ScriptExecutionResult> future() {
            if (immediate != null) {
                return CompletableFuture.completedFuture(immediate);
            }
            return invocation.resultFuture.thenCompose(result ->
                    invocation.prepared.deferredOperations().drain(result));
        }
    }

    private final class Invocation implements Runnable {

        private final long id;
        private final PreparedInvocation prepared;
        private final boolean silent;
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean completed = new AtomicBoolean();
        private final AtomicBoolean guestRunning = new AtomicBoolean();
        private final AtomicReference<CancellationReason> cancellation = new AtomicReference<>(CancellationReason.NONE);
        private final AtomicReference<Context> context = new AtomicReference<>();
        private final CountDownLatch completionLatch = new CountDownLatch(1);
        private final CompletableFuture<ScriptExecutionResult> resultFuture = new CompletableFuture<>();
        private volatile ScheduledFuture<?> timeoutTask;
        private volatile ScriptExecutionResult result;

        private Invocation(long id, PreparedInvocation prepared, boolean silent) {
            this.id = id;
            this.prepared = prepared;
            this.silent = silent;
        }

        @Override
        public void run() {
            if (!started.compareAndSet(false, true)) {
                return;
            }
            ScriptWorkerBoundary.enter();
            Context localContext = null;
            ScriptExecutionResult outcome = null;
            try {
                CancellationReason beforeStart = cancellation.get();
                if (beforeStart != CancellationReason.NONE || state.get() != ServiceState.ACTIVE) {
                    if (beforeStart == CancellationReason.NONE) {
                        cancellation.compareAndSet(CancellationReason.NONE, CancellationReason.DISABLED);
                    }
                    outcome = cancellationResult(cancellation.get(), prepared.timeoutMillis());
                    return;
                }
                localContext = createContext();
                context.set(localContext);
                guestRunning.set(true);
                CancellationReason afterContext = cancellation.get();
                if (afterContext != CancellationReason.NONE || state.get() != ServiceState.ACTIVE) {
                    if (afterContext == CancellationReason.NONE) {
                        cancellation.compareAndSet(CancellationReason.NONE, CancellationReason.DISABLED);
                    }
                    requestCancellation(cancellation.get());
                    outcome = cancellationResult(cancellation.get(), prepared.timeoutMillis());
                    return;
                }
                timeoutTask = watchdogExecutor.schedule(
                        () -> requestCancellation(CancellationReason.TIMEOUT),
                        prepared.timeoutMillis(),
                        TimeUnit.MILLISECONDS
                );
                outcome = executePrepared(this, localContext);
            } catch (Throwable throwable) {
                CancellationReason reason = cancellation.get();
                if (reason != CancellationReason.NONE) {
                    outcome = cancellationResult(reason, prepared.timeoutMillis());
                } else {
                    if (!silent) {
                        warn("Script execution failed: " + prepared.source().logicalPath() + "#"
                                + prepared.functionName() + " - " + exceptionMessage(throwable));
                        if (config.debug().printStacktrace()) {
                            throwable.printStackTrace();
                        }
                    }
                    String prefix = throwable instanceof PolyglotException
                            ? "Script execution failed: "
                            : "Script invocation failed: ";
                    outcome = ScriptExecutionResult.failure(prefix + exceptionMessage(throwable), throwable);
                }
            } finally {
                guestCompleted();
                Context captured = context.getAndSet(null);
                if (captured != null) {
                    try {
                        captured.close(false);
                    } catch (RuntimeException ignored) {
                    }
                } else if (localContext != null) {
                    try {
                        localContext.close(false);
                    } catch (RuntimeException ignored) {
                    }
                }
                CancellationReason finalReason = cancellation.get();
                if (finalReason != CancellationReason.NONE) {
                    outcome = cancellationResult(finalReason, prepared.timeoutMillis());
                } else if (outcome == null) {
                    outcome = ScriptExecutionResult.failure("Script execution ended without a result.");
                }
                try {
                    complete(outcome);
                } finally {
                    ScriptWorkerBoundary.exit();
                }
            }
        }

        private void guestCompleted() {
            guestRunning.set(false);
            ScheduledFuture<?> scheduled = timeoutTask;
            if (scheduled != null) {
                scheduled.cancel(false);
                timeoutTask = null;
            }
        }

        private void requestCancellation(CancellationReason reason) {
            if (reason == null || reason == CancellationReason.NONE || completed.get()) {
                return;
            }
            cancellation.compareAndSet(CancellationReason.NONE, reason);
            Context activeContext = context.get();
            if (activeContext != null && guestRunning.get()) {
                try {
                    activeContext.close(true);
                } catch (RuntimeException ignored) {
                }
            }
        }

        private void abortBeforeStart(CancellationReason reason) {
            requestCancellation(reason);
            if (started.compareAndSet(false, true)) {
                complete(cancellationResult(cancellation.get(), prepared.timeoutMillis()));
            }
        }

        private ScriptExecutionResult awaitResult() {
            long waitMillis = Math.max(1L, prepared.timeoutMillis()) + RESULT_WAIT_GRACE_MILLIS;
            try {
                if (completionLatch.await(waitMillis, TimeUnit.MILLISECONDS)) {
                    return result;
                }
                requestCancellation(CancellationReason.TIMEOUT);
                completionLatch.await(RESULT_WAIT_GRACE_MILLIS, TimeUnit.MILLISECONDS);
                ScriptExecutionResult completedResult = result;
                return completedResult == null
                        ? cancellationResult(CancellationReason.TIMEOUT, prepared.timeoutMillis())
                        : completedResult;
            } catch (InterruptedException exception) {
                requestCancellation(CancellationReason.INTERRUPTED);
                Thread.currentThread().interrupt();
                return ScriptExecutionResult.failure("JavaScript invocation was interrupted.", exception);
            }
        }

        private void complete(ScriptExecutionResult outcome) {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            result = outcome;
            activeInvocations.remove(id, this);
            resultFuture.complete(outcome);
            completionLatch.countDown();
        }
    }
}
