package emaki.jiuwu.craft.corelib.script.host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ScriptHost implements AutoCloseable {

    private static final String LANGUAGE_ID = "js";
    private static final String LIB_DIRECTORY = "lib";
    private static final String SCRIPT_EXTENSION = ".js";
    private static final String LOGGER_BINDING = "logger";
    private static final String JS_LANGUAGE_CLASS = "com.oracle.truffle.js.lang.JavaScriptLanguage";
    private static final long INTERRUPT_GRACE_MS = 100L;

    private final ScriptHostSettings settings;
    private final Path scriptsDirectory;
    private final Map<String, Object> bindings;
    private final Logger logger;
    private final Engine engine;
    private final ScheduledExecutorService watchdog;
    private final ReentrantLock lock = new ReentrantLock();
    private final ScriptCallbackRunner callbackRunner = new ScriptCallbackRunner(this);

    private boolean closed;
    private Context context;
    private volatile boolean interruptDispatched;

    public ScriptHost(@Nullable Plugin owner,
                      @NotNull Path scriptsDirectory,
                      @NotNull ScriptHostSettings settings,
                      @NotNull Map<String, Object> bindings) {
        this.settings = settings;
        this.scriptsDirectory = scriptsDirectory;
        this.bindings = Map.copyOf(bindings);
        this.logger = createLogger(owner);
        this.engine = Engine.newBuilder(LANGUAGE_ID)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        this.watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Emaki-ScriptHost-Watchdog");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static boolean isEngineAvailable() {
        try {
            Class.forName(JS_LANGUAGE_CLASS, true, ScriptHost.class.getClassLoader());
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    public @NotNull ScriptLoadReport load() {
        lock.lock();
        try {
            if (closed) {
                return ScriptLoadReport.empty();
            }
            if (context == null) {
                context = createContext();
                injectBindings(context);
            }
            return evaluateAll();
        } finally {
            lock.unlock();
        }
    }

    public @NotNull ScriptLoadReport reload() {
        lock.lock();
        try {
            closeContext();
            if (closed) {
                return ScriptLoadReport.empty();
            }
            context = createContext();
            injectBindings(context);
            return evaluateAll();
        } finally {
            lock.unlock();
        }
    }

    public @NotNull ScriptCallbackRunner callbackRunner() {
        return callbackRunner;
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            closeContext();
        } finally {
            lock.unlock();
        }
        engine.close();
        watchdog.shutdownNow();
    }

    void lockExclusive() {
        lock.lock();
    }

    void unlockExclusive() {
        lock.unlock();
    }

    @Nullable Context currentContext() {
        return context;
    }

    ScheduledFuture<?> scheduleInterrupt(Context target) {
        interruptDispatched = false;
        return watchdog.schedule(() -> {
            interruptDispatched = true;
            try {
                target.interrupt(Duration.ofMillis(INTERRUPT_GRACE_MS));
            } catch (TimeoutException | IllegalStateException ignored) {

            }
        }, settings.timeoutMs(), TimeUnit.MILLISECONDS);
    }

    private Context createContext() {
        return Context.newBuilder(LANGUAGE_ID)
                .engine(engine)
                .allowAllAccess(true)
                .build();
    }

    private void injectBindings(Context target) {
        Value jsBindings = target.getBindings(LANGUAGE_ID);
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            jsBindings.putMember(entry.getKey(), entry.getValue());
        }
        jsBindings.putMember(LOGGER_BINDING, new HostLogger(logger));
    }

    private ScriptLoadReport evaluateAll() {
        List<Path> files;
        try {
            files = scanScripts();
        } catch (IOException exception) {
            return new ScriptLoadReport(0, 1, List.of(new ScriptLoadReport.ScriptLoadError(
                    scriptsDirectory.toString(), errorMessage(exception))));
        }
        Context current = context;
        int filesLoaded = 0;
        List<ScriptLoadReport.ScriptLoadError> errors = new ArrayList<>();
        for (Path file : files) {
            String name = relativeName(file);
            try {
                Source source = Source.newBuilder(LANGUAGE_ID, file.toFile()).name(name).build();
                ScheduledFuture<?> pendingInterrupt = scheduleInterrupt(current);
                try {
                    current.eval(source);
                    filesLoaded++;
                } finally {
                    pendingInterrupt.cancel(false);
                }
            } catch (PolyglotException exception) {
                errors.add(new ScriptLoadReport.ScriptLoadError(name, describePolyglot(exception)));
            } catch (IOException exception) {
                errors.add(new ScriptLoadReport.ScriptLoadError(name, errorMessage(exception)));
            } catch (RuntimeException exception) {
                errors.add(new ScriptLoadReport.ScriptLoadError(name, errorMessage(exception)));
            }
        }
        return new ScriptLoadReport(filesLoaded, errors.size(), errors);
    }

    private List<Path> scanScripts() throws IOException {
        if (!Files.isDirectory(scriptsDirectory)) {
            return List.of();
        }
        List<Path> ordered = new ArrayList<>();
        Path libDirectory = scriptsDirectory.resolve(LIB_DIRECTORY);
        if (Files.isDirectory(libDirectory)) {
            ordered.addAll(listJsFiles(libDirectory));
        }
        ordered.addAll(listJsFiles(scriptsDirectory));
        for (Path directory : listSubDirectories(scriptsDirectory)) {
            ordered.addAll(listJsFiles(directory));
        }
        return List.copyOf(ordered);
    }

    private List<Path> listJsFiles(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(SCRIPT_EXTENSION))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private List<Path> listSubDirectories(Path parent) throws IOException {
        try (Stream<Path> stream = Files.list(parent)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(directory -> !directory.getFileName().toString().equals(LIB_DIRECTORY))
                    .sorted(Comparator.comparing(directory -> directory.getFileName().toString()))
                    .toList();
        }
    }

    private String relativeName(Path file) {
        return scriptsDirectory.relativize(file).toString().replace('\\', '/');
    }

    private String describePolyglot(PolyglotException exception) {
        if (isTimeoutInterruption(exception)) {
            return "脚本求值超时，超过 " + settings.timeoutMs() + " ms";
        }
        String text = errorMessage(exception);
        SourceSection location = exception.getSourceLocation();
        if (location != null) {
            String locationText = location.toString();
            if (locationText != null && !locationText.isBlank()) {
                return text + " (" + locationText + ")";
            }
        }
        return text;
    }

    boolean isTimeoutInterruption(PolyglotException exception) {
        return exception.isCancelled() || (exception.isInterrupted() && interruptDispatched);
    }

    private static String errorMessage(Throwable exception) {
        String message = exception.getMessage();
        return message != null && !message.isBlank() ? message : exception.toString();
    }

    private void closeContext() {
        Context current = context;
        context = null;
        if (current != null) {
            current.close();
        }
    }

    private static Logger createLogger(@Nullable Plugin owner) {
        if (owner != null) {
            return owner.getLogger();
        }
        Logger silent = Logger.getAnonymousLogger();
        silent.setUseParentHandlers(false);
        silent.setLevel(Level.OFF);
        return silent;
    }

    public static final class HostLogger {

        private final Logger logger;

        HostLogger(@NotNull Logger logger) {
            this.logger = logger;
        }

        public void info(@NotNull String message) {
            logger.info(message);
        }

        public void warn(@NotNull String message) {
            logger.warning(message);
        }
    }
}
