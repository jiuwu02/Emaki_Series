package emaki.jiuwu.craft.dummy.scenario;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.bukkit.command.CommandSender;

import emaki.jiuwu.craft.dummy.config.AppConfig;
import emaki.jiuwu.craft.dummy.listener.ServerEventBridge;
import emaki.jiuwu.craft.dummy.log.DummyLogger;
import emaki.jiuwu.craft.dummy.log.LogLevel;
import emaki.jiuwu.craft.dummy.session.DummyManager;

public final class ScenarioEngine
        implements ExpressionContext, ScenarioRuntime, ServerEventBridge.EventObserver {

    public enum RunOutcome {
        STARTED,
        NOT_FOUND,
        BUSY
    }

    public record CompletedResult(String id, String result, int passed, int failed, long elapsedMs) {
    }

    private static final int EVENT_DETAIL_CAP = 512;
    private static final int TAIL_BYTES = 65536;

    private final Supplier<AppConfig> config;
    private final DummyManager manager;
    private final DummyLogger logger;
    private final Path serverRoot;

    private final Map<String, ScenarioDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, Integer> eventCounters = new ConcurrentHashMap<>();
    private final Map<String, List<String>> eventDetails = new ConcurrentHashMap<>();
    private final Set<String> unresolvedTypes = ConcurrentHashMap.newKeySet();
    private final List<String> loadFailures = new CopyOnWriteArrayList<>();

    private volatile ServerEventBridge bridge;
    private volatile ScenarioRunner activeRunner;
    private volatile CommandSender activeRecipient;
    private volatile CompletedResult completedResult;

    public ScenarioEngine(Supplier<AppConfig> config, DummyManager manager,
            DummyLogger logger, Path serverRoot) {
        this.config = config;
        this.manager = manager;
        this.logger = logger;
        this.serverRoot = serverRoot;
    }

    public void bridge(ServerEventBridge bridge) {
        this.bridge = bridge;
    }

    public int reload(Path scenariosDir) {
        definitions.clear();
        loadFailures.clear();
        int loaded = 0;
        File[] files = scenariosDir.toFile()
                .listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            try {
                ScenarioDefinition definition = ScenarioLoader.load(file);
                definitions.put(definition.id(), definition);
                loaded++;
            } catch (ScenarioLoadException failure) {
                loadFailures.add(file.getName() + ": " + failure.getMessage());
                logger.log(LogLevel.ERROR, "scenario", "load_failed file=" + file.getName()
                        + " reason=" + failure.getMessage());
            }
        }
        return loaded;
    }

    public Collection<String> ids() {
        return definitions.keySet();
    }

    public ScenarioDefinition definition(String id) {
        return definitions.get(id);
    }

    public List<String> loadFailures() {
        return List.copyOf(loadFailures);
    }

    public ScenarioRunner active() {
        return activeRunner;
    }

    public synchronized RunOutcome run(String id, CommandSender recipient) {
        ScenarioDefinition definition = definitions.get(id);
        if (definition == null) {
            return RunOutcome.NOT_FOUND;
        }
        ScenarioRunner current = activeRunner;
        if (current != null && !current.isDone()) {
            return RunOutcome.BUSY;
        }
        resetObservations();
        logger.clearRecent();
        for (ScenarioStep step : definition.steps()) {
            if ("wait_event".equals(step.action())) {
                ensureWatching(step.param("type"));
            }
        }
        activeRecipient = recipient;
        ScenarioRunner runner = new ScenarioRunner(definition, manager, logger, this, this);
        activeRunner = runner;
        logger.log(LogLevel.SCENARIO, id, "start players=" + String.join(",", definition.players())
                + " steps=" + definition.steps().size()
                + " timeout=" + definition.timeoutSeconds() + "s");
        return RunOutcome.STARTED;
    }

    public synchronized boolean stop(String id) {
        ScenarioRunner current = activeRunner;
        if (current == null || current.isDone() || !current.id().equals(id)) {
            return false;
        }
        current.requestStop();
        return true;
    }

    public synchronized void stopActive() {
        ScenarioRunner current = activeRunner;
        if (current != null && !current.isDone()) {
            current.requestStop();
        }
    }

    public synchronized void tick() {
        ScenarioRunner current = activeRunner;
        if (current == null) {
            return;
        }
        current.tick();
        if (current.isDone()) {
            completedResult = new CompletedResult(current.id(), current.result(),
                    current.passedCount(), current.failedCount(), current.elapsedMs());
            activeRunner = null;
        }
    }

    public CommandSender drainRecipient() {
        CommandSender recipient = activeRecipient;
        activeRecipient = null;
        return recipient;
    }

    public CompletedResult drainCompleted() {
        CompletedResult result = completedResult;
        completedResult = null;
        return result;
    }

    @Override
    public void onObservedEvent(String type, String detail) {
        eventCounters.merge(type, 1, Integer::sum);
        List<String> details = eventDetails.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>());
        details.add(detail);
        while (details.size() > EVENT_DETAIL_CAP) {
            details.remove(0);
        }
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public double eventCount(String type) {
        return eventCounters.getOrDefault(type, 0);
    }

    @Override
    public boolean eventMatched(String type, String where, double sinceCount) {
        List<String> details = eventDetails.get(type);
        if (details == null || details.isEmpty()) {
            return false;
        }
        int start = (int) Math.max(0, Math.min(sinceCount, details.size()));
        for (int index = start; index < details.size(); index++) {
            String detail = details.get(index);
            if (where == null || where.isBlank() || detail.contains(where)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean logContains(String fileGlob, String contains) {
        if (fileGlob == null || contains == null) {
            return false;
        }
        Path target = resolveNewest(fileGlob);
        return target != null && tailContains(target, contains);
    }

    @Override
    public boolean auditContains(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String glob : config.get().scenarioAuditFiles()) {
            Path target = resolveNewest(glob);
            if (target != null && tailContains(target, text)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean recvContains(String name, String text) {
        return logger.recentMatch("RECV", name, text);
    }

    @Override
    public void ensureWatching(String type) {
        if (type == null || type.isBlank() || bridge == null) {
            return;
        }
        if (bridge.ensureWatching(type) == null && unresolvedTypes.add(type)) {
            logger.log(LogLevel.ERROR, "scenario", "unresolved_event_type type=" + type);
        }
    }

    @Override
    public double onlineCount() {
        return manager.onlineCount();
    }

    @Override
    public double dummyOnline(String name) {
        return manager.find(name) != null && manager.find(name).isOnline() ? 1.0D : 0.0D;
    }

    @Override
    public double inventoryCount(String name) {
        int[] holder = new int[1];
        if (!manager.inventoryCount(name, holder)) {
            return -1.0D;
        }
        return holder[0];
    }

    private void resetObservations() {
        eventCounters.clear();
        eventDetails.clear();
        unresolvedTypes.clear();
    }

    private Path resolveNewest(String glob) {
        String normalized = glob.replace('\\', '/');
        int starIndex = normalized.indexOf('*');
        if (starIndex < 0) {
            Path direct = toPath(normalized);
            return Files.isRegularFile(direct) ? direct : null;
        }
        int slashIndex = normalized.lastIndexOf('/', starIndex);
        String dirPart = slashIndex >= 0 ? normalized.substring(0, slashIndex) : "";
        String patternPart = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        Path directory = dirPart.isEmpty() ? serverRoot : toPath(dirPart);
        if (!Files.isDirectory(directory)) {
            return null;
        }
        Pattern matcher = Pattern.compile(globToRegex(patternPart));
        Path newest = null;
        long newestTime = -1L;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                String name = entry.getFileName().toString();
                if (!matcher.matcher(name).matches()) {
                    continue;
                }
                long modified = Files.getLastModifiedTime(entry).toMillis();
                if (modified > newestTime) {
                    newestTime = modified;
                    newest = entry;
                }
            }
        } catch (IOException failure) {
            logger.log(LogLevel.ERROR, "scenario", "glob_scan_failed glob=" + glob
                    + " reason=" + failure.getMessage());
            return null;
        }
        return newest;
    }

    private Path toPath(String relativeOrAbsolute) {
        Path candidate = Path.of(relativeOrAbsolute);
        return candidate.isAbsolute() ? candidate : serverRoot.resolve(candidate);
    }

    private boolean tailContains(Path file, String needle) {
        try (RandomAccessFile reader = new RandomAccessFile(file.toFile(), "r")) {
            long length = reader.length();
            int offset = (int) Math.max(0, length - TAIL_BYTES);
            reader.seek(offset);
            byte[] buffer = new byte[(int) (length - offset)];
            reader.readFully(buffer);
            String text = new String(buffer, StandardCharsets.UTF_8);
            return text.contains(needle);
        } catch (IOException failure) {
            return false;
        }
    }

    static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder(glob.length() * 2);
        for (int index = 0; index < glob.length(); index++) {
            char ch = glob.charAt(index);
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> {
                    if ("\\.[]{}()+-^$|".indexOf(ch) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(ch);
                }
            }
        }
        return regex.toString();
    }

    public void shutdown() {
        stopActive();
        activeRecipient = null;
    }

    public Path serverRoot() {
        return serverRoot;
    }
}
