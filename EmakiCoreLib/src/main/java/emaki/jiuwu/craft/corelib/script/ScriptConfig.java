package emaki.jiuwu.craft.corelib.script;

import java.util.List;
import java.util.Locale;

import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public record ScriptConfig(boolean enabled,
        Engine engine,
        Paths paths,
        Action action,
        Context context,
        Security security,
        ServerApi serverApi,
        Debug debug) {

    public static ScriptConfig defaults() {
        return new ScriptConfig(
                false,
                Engine.defaults(),
                Paths.defaults(),
                Action.defaults(),
                Context.defaults(),
                Security.defaults(),
                ServerApi.defaults(),
                Debug.defaults()
        );
    }

    public static ScriptConfig fromConfig(YamlSection section) {
        ScriptConfig defaults = defaults();
        if (section == null) {
            return defaults;
        }
        return new ScriptConfig(
                section.getBoolean("enabled", defaults.enabled()),
                Engine.fromConfig(section.getSection("engine")),
                Paths.fromConfig(section.getSection("paths")),
                Action.fromConfig(section.getSection("action")),
                Context.fromConfig(section.getSection("context")),
                Security.fromConfig(section.getSection("security")),
                ServerApi.fromConfig(section.getSection("server_api")),
                Debug.fromConfig(section.getSection("debug"))
        );
    }

    public long clampTimeoutMillis(long requested) {
        long fallback = engine.defaultTimeoutMillis();
        long value = requested <= 0L ? fallback : requested;
        return Math.max(1L, Math.min(value, engine.maxTimeoutMillis()));
    }

    public record Engine(String type,
            long defaultTimeoutMillis,
            long maxTimeoutMillis,
            boolean cacheEnabled,
            boolean recompileOnReload,
            boolean allowHostAccess,
            boolean allowHostClassLookup,
            boolean allowIo,
            boolean allowThreads,
            boolean allowNativeAccess,
            boolean allowEnvironmentAccess) {

        public static Engine defaults() {
            return new Engine("graaljs", 1_000L, 5_000L, true, true,
                    false, false, false, false, false, false);
        }

        public static Engine fromConfig(YamlSection section) {
            Engine defaults = defaults();
            if (section == null) {
                return defaults;
            }
            long defaultTimeout = parseMillis(section.getString("default_timeout_millis", Long.toString(defaults.defaultTimeoutMillis())), defaults.defaultTimeoutMillis());
            long maxTimeout = parseMillis(section.getString("max_timeout_millis", Long.toString(defaults.maxTimeoutMillis())), defaults.maxTimeoutMillis());
            if (maxTimeout < defaultTimeout) {
                maxTimeout = defaultTimeout;
            }
            return new Engine(
                    Texts.isBlank(section.getString("type")) ? defaults.type() : Texts.lower(section.getString("type")),
                    Math.max(1L, defaultTimeout),
                    Math.max(1L, maxTimeout),
                    section.getBoolean("cache_enabled", defaults.cacheEnabled()),
                    section.getBoolean("recompile_on_reload", defaults.recompileOnReload()),
                    section.getBoolean("allow_host_access", defaults.allowHostAccess()),
                    section.getBoolean("allow_host_class_lookup", defaults.allowHostClassLookup()),
                    section.getBoolean("allow_io", defaults.allowIo()),
                    section.getBoolean("allow_threads", defaults.allowThreads()),
                    section.getBoolean("allow_native_access", defaults.allowNativeAccess()),
                    section.getBoolean("allow_environment_access", defaults.allowEnvironmentAccess())
            );
        }
    }

    public record Paths(String root, List<String> createDirectories) {

        public static Paths defaults() {
            return new Paths("scripts", List.of("global", "mythic", "extensions/global", "templates", "examples"));
        }

        public static Paths fromConfig(YamlSection section) {
            Paths defaults = defaults();
            if (section == null) {
                return defaults;
            }
            String root = Texts.trim(section.getString("root", defaults.root()));
            List<String> directories = section.getStringList("create_directories");
            return new Paths(Texts.isBlank(root) ? defaults.root() : root,
                    directories == null || directories.isEmpty() ? defaults.createDirectories() : List.copyOf(directories));
        }
    }

    public record Action(String id, List<String> aliases, String defaultFunction, boolean stopOnFailure) {

        public static Action defaults() {
            return new Action("runjs", List.of(), "main", true);
        }

        public static Action fromConfig(YamlSection section) {
            Action defaults = defaults();
            if (section == null) {
                return defaults;
            }
            String id = Texts.normalizeId(section.getString("id", defaults.id()));
            String defaultFunction = Texts.trim(section.getString("default_function", defaults.defaultFunction()));
            List<String> aliases = section.getStringList("aliases").stream()
                    .map(Texts::normalizeId)
                    .filter(Texts::isNotBlank)
                    .distinct()
                    .toList();
            return new Action(Texts.isBlank(id) ? defaults.id() : id,
                    aliases.isEmpty() ? defaults.aliases() : aliases,
                    Texts.isBlank(defaultFunction) ? defaults.defaultFunction() : defaultFunction,
                    section.getBoolean("stop_on_failure", defaults.stopOnFailure()));
        }
    }

    public record Context(boolean exposeContext,
            boolean exposePlayer,
            boolean exposeItem,
            boolean exposeAction,
            boolean exposeLogger,
            boolean exposeRandom,
            boolean exposeSharedState,
            boolean exposeText) {

        public static Context defaults() {
            return new Context(true, true, true, true, true, true, true, true);
        }

        public static Context fromConfig(YamlSection section) {
            Context defaults = defaults();
            if (section == null) {
                return defaults;
            }
            return new Context(
                    section.getBoolean("expose_context", defaults.exposeContext()),
                    section.getBoolean("expose_player", defaults.exposePlayer()),
                    section.getBoolean("expose_item", defaults.exposeItem()),
                    section.getBoolean("expose_action", defaults.exposeAction()),
                    section.getBoolean("expose_logger", defaults.exposeLogger()),
                    section.getBoolean("expose_random", defaults.exposeRandom()),
                    section.getBoolean("expose_shared_state", defaults.exposeSharedState()),
                    section.getBoolean("expose_text", defaults.exposeText())
            );
        }
    }

    public record Security(List<String> deniedPathFragments,
            List<String> deniedActionsFromScript,
            boolean allowActionDispatch,
            int maxActionDepth) {

        public static Security defaults() {
            return new Security(List.of("..", ":", "\\"), List.of("runjs"), true, 3);
        }

        public static Security fromConfig(YamlSection section) {
            Security defaults = defaults();
            if (section == null) {
                return defaults;
            }
            List<String> deniedPathFragments = section.getStringList("denied_path_fragments");
            List<String> deniedActions = section.getStringList("denied_actions_from_script").stream()
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .toList();
            return new Security(
                    deniedPathFragments == null || deniedPathFragments.isEmpty() ? defaults.deniedPathFragments() : List.copyOf(deniedPathFragments),
                    deniedActions.isEmpty() ? defaults.deniedActionsFromScript() : List.copyOf(deniedActions),
                    section.getBoolean("allow_action_dispatch", defaults.allowActionDispatch()),
                    Math.max(0, Numbers.tryParseInt(section.get("max_action_depth"), defaults.maxActionDepth()))
            );
        }
    }

    public record ServerApi(boolean enabled,
            boolean allowTypeAccess,
            List<String> allowedTypePrefixes,
            boolean allowConsoleCommand,
            boolean allowRawEventAccess) {

        public static ServerApi defaults() {
            return new ServerApi(false, false, List.of("org.bukkit.", "io.papermc.paper."), false, false);
        }

        public static ServerApi fromConfig(YamlSection section) {
            ServerApi defaults = defaults();
            if (section == null) {
                return defaults;
            }
            List<String> prefixes = section.getStringList("allowed_type_prefixes").stream()
                    .map(Texts::trim)
                    .filter(Texts::isNotBlank)
                    .toList();
            return new ServerApi(
                    section.getBoolean("enabled", defaults.enabled()),
                    section.getBoolean("allow_type_access", defaults.allowTypeAccess()),
                    prefixes.isEmpty() ? defaults.allowedTypePrefixes() : List.copyOf(prefixes),
                    section.getBoolean("allow_console_command", defaults.allowConsoleCommand()),
                    section.getBoolean("allow_raw_event_access", defaults.allowRawEventAccess())
            );
        }
    }

    public record Debug(boolean logScriptLoad, boolean logScriptExecute, boolean printStacktrace) {

        public static Debug defaults() {
            return new Debug(true, false, false);
        }

        public static Debug fromConfig(YamlSection section) {
            Debug defaults = defaults();
            if (section == null) {
                return defaults;
            }
            return new Debug(
                    section.getBoolean("log_script_load", defaults.logScriptLoad()),
                    section.getBoolean("log_script_execute", defaults.logScriptExecute()),
                    section.getBoolean("print_stacktrace", defaults.printStacktrace())
            );
        }
    }

    public static long parseMillis(String raw, long fallback) {
        if (Texts.isBlank(raw)) {
            return fallback;
        }
        String trimmed = Texts.trim(raw).toLowerCase(Locale.ROOT);
        if (trimmed.endsWith("ms")) {
            return Math.max(0L, Math.round(Numbers.tryParseDouble(trimmed.substring(0, trimmed.length() - 2), (double) fallback)));
        }
        long ticks = ActionParsers.parseTicks(trimmed);
        if (ticks >= 0L && (trimmed.endsWith("s") || trimmed.endsWith("t"))) {
            return ticks * 50L;
        }
        return Math.max(0L, Math.round(Numbers.tryParseDouble(trimmed, (double) fallback)));
    }
}
