package emaki.jiuwu.craft.item.script;

import emaki.jiuwu.craft.item.script.js.JavaScriptItemDefinitionRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

public final class JavaScriptItemFactoryRegistry {


    private static final String REGISTRATION_TYPE = "item_factory";

    private final EmakiItemPlugin plugin;
    private final JavaScriptItemDefinitionRegistry definitionRegistry;
    private final Map<String, FactoryEntry> factories = new LinkedHashMap<>();

    public JavaScriptItemFactoryRegistry(EmakiItemPlugin plugin, JavaScriptItemDefinitionRegistry definitionRegistry) {
        this.plugin = plugin;
        this.definitionRegistry = definitionRegistry;
    }

    public synchronized boolean register(ScriptModuleContext context, Map<String, ?> definition, JavaScriptRegistrationTracker tracker) {
        if (definition == null) {
            recordError(context, tracker, "", "register", "Item factory definition cannot be null.");
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            recordError(context, tracker, id, "register", "Item factory id cannot be blank.");
            return false;
        }
        String function = value(definition, "function", value(definition, "execute", "create"));
        long timeoutMillis = longValue(definition.get("timeoutMillis"), context == null ? 1_000L : context.config().engine().defaultTimeoutMillis());
        long started = System.nanoTime();
        int priority = intValue(definition.get("priority"), 0);
        factories.put(id, new FactoryEntry(id, priority, function, scriptPath(context), timeoutMillis));
        if (tracker != null && !tracker.register(plugin,
                scriptPath(context),
                REGISTRATION_TYPE,
                id,
                elapsedMillis(started),
                () -> unregister(id),
                Map.of("function", function, "priority", priority))) {
            factories.remove(id);
            return false;
        }
        return true;
    }

    public synchronized void unregister(String id) {
        factories.remove(Texts.normalizeId(id));
    }

    public synchronized void clear() {
        factories.clear();
    }

    public synchronized List<String> ids() {
        return factories.keySet().stream().sorted().toList();
    }

    public ItemStack create(String id, int amount) {
        if (plugin == null || plugin.coreLib() == null) {
            return null;
        }
        JavaScriptService javaScriptService = plugin.coreLib().javaScriptService();
        if (javaScriptService == null || !javaScriptService.enabled()) {
            return null;
        }
        for (FactoryEntry entry : matchingFactories()) {
            ItemStack itemStack = create(javaScriptService, entry, id, amount);
            if (itemStack != null) {
                return itemStack;
            }
        }
        return null;
    }

    private ItemStack create(JavaScriptService javaScriptService, FactoryEntry entry, String requestedId, int amount) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("id", Texts.normalizeId(requestedId));
        arguments.put("factoryId", entry.id());
        arguments.put("amount", Math.max(1, amount));
        arguments.put("script", entry.scriptPath());
        Player player = firstOnlinePlayer();
        if (player != null) {
            arguments.put("playerUuid", player.getUniqueId().toString());
            arguments.put("playerName", player.getName());
        }
        Map<String, Object> snapshot = ScriptSnapshots.immutableMap(arguments);
        ScriptConfig config = plugin.coreLib().configModel() == null ? ScriptConfig.defaults() : plugin.coreLib().configModel().scriptConfig();
        ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                plugin,
                null,
                entry.scriptPath(),
                entry.functionName(),
                List.of(snapshot),
                snapshot,
                config.clampTimeoutMillis(entry.timeoutMillis()),
                false
        ));
        if (result == null || !result.success() || !(result.returnValue() instanceof Map<?, ?> rawMap)) {
            if (result != null && !result.success()) {
                plugin.getLogger().warning("[JavaScript] Item factory '" + entry.id() + "' failed: " + result.message());
            }
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        ConfigNodes.entries(rawMap).forEach(map::put);
        map.putIfAbsent("id", Texts.normalizeId(requestedId));
        EmakiItemDefinition definition = definitionRegistry.parseDefinition(Texts.normalizeId(Texts.toStringSafe(map.get("id"))), map, null, null);
        if (definition == null || plugin.itemFactory() == null) {
            return null;
        }

        int resolved = amount > 0 ? amount : definition.amount();
        return plugin.itemFactory().rebuildBase(definition, resolved);
    }

    private synchronized List<FactoryEntry> matchingFactories() {
        return factories.values().stream()
                .sorted((left, right) -> {
                    int priority = Integer.compare(left.priority(), right.priority());
                    return priority != 0 ? priority : left.id().compareTo(right.id());
                })
                .toList();
    }

    private Player firstOnlinePlayer() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            return player;
        }
        return null;
    }

    private void recordError(ScriptModuleContext context, JavaScriptRegistrationTracker tracker, String id, String phase, String message) {
        if (tracker != null) {
            tracker.recordError(scriptPath(context), REGISTRATION_TYPE, id, phase, message);
        }
        if (plugin != null) {
            plugin.getLogger().warning("[JavaScript] Item factory registration failed: " + message);
        }
    }

    private static String value(Map<String, ?> map, String key, String fallback) {
        Object value = map == null ? null : map.get(key);
        String text = Texts.toStringSafe(value);
        return Texts.isBlank(text) ? fallback : text;
    }

    private static long longValue(Object raw, long fallback) {
        if (raw instanceof Number number) {
            return Math.max(1L, number.longValue());
        }
        try {
            return Math.max(1L, Long.parseLong(Texts.toStringSafe(raw)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static int intValue(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Texts.toStringSafe(raw));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String scriptPath(ScriptModuleContext context) {
        return context == null ? "" : context.scriptPath();
    }

    private static long elapsedMillis(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private record FactoryEntry(String id, int priority, String functionName, String scriptPath, long timeoutMillis) {
    }
}
