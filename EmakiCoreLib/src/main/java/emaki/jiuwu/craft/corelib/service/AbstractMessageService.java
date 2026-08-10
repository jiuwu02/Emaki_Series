package emaki.jiuwu.craft.corelib.service;

import java.util.Objects;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.DiagnosticRenderer;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

public class AbstractMessageService implements LogMessages {

    private final JavaPlugin plugin;
    private final String defaultPrefix;
    private final Function<String, String> messageResolver;
    private final BiFunction<String, Map<String, ?>, String> replacementResolver;
    private final boolean includePrefixInLogs;

    /**
     * Renders pipeline diagnostics through this service's language file.
     *
     * <p>Held here rather than behind a plugin-level accessor because every display site already reaches a
     * message service, so no second wiring path is needed and a reload replaces the renderer along with the
     * service that owns it.</p>
     */
    private final DiagnosticRenderer diagnosticRenderer = new DiagnosticRenderer(this::messageOrFallback);

    protected AbstractMessageService(JavaPlugin plugin, String defaultPrefix) {
        this(plugin, defaultPrefix, null, null, false);
    }

    public AbstractMessageService(JavaPlugin plugin,
            String defaultPrefix,
            Function<String, String> messageResolver,
            BiFunction<String, Map<String, ?>, String> replacementResolver) {
        this(plugin, defaultPrefix, messageResolver, replacementResolver, false);
    }

    public AbstractMessageService(JavaPlugin plugin,
            String defaultPrefix,
            Function<String, String> messageResolver,
            BiFunction<String, Map<String, ?>, String> replacementResolver,
            boolean includePrefixInLogs) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.defaultPrefix = Texts.toStringSafe(defaultPrefix);
        this.messageResolver = messageResolver;
        this.replacementResolver = replacementResolver;
        this.includePrefixInLogs = includePrefixInLogs;
    }

    @Override
    public final String message(String key) {
        return resolveMessage(key);
    }

    @Override
    public final String message(String key, Map<String, ?> replacements) {
        return resolveMessage(key, replacements == null ? Map.of() : replacements);
    }

    public final String messageOrFallback(String key, String fallback) {
        if (Texts.isBlank(key)) {
            return fallback;
        }
        String value = message(key);
        return Texts.isBlank(value) || key.equals(value) ? fallback : value;
    }

    /**
     * Resolves a key with replacements, returning {@code fallback} when the key has no translation.
     *
     * <p>Shares the missing-key test with {@link #messageOrFallback(String, String)}: the language loader
     * returns the key itself when it cannot find an entry, so a resolved value equal to the key means the
     * translation is absent. The single-argument form covers static labels; diagnostics need placeholders,
     * which is why this overload exists.</p>
     *
     * @param key the language key
     * @param replacements placeholder replacements
     * @param fallback returned when the key has no translation
     * @return the resolved message, or {@code fallback}
     */
    public final String messageOrFallback(String key, Map<String, ?> replacements, String fallback) {
        if (Texts.isBlank(key)) {
            return fallback;
        }
        String value = message(key, replacements == null ? Map.of() : replacements);
        return Texts.isBlank(value) || key.equals(value) ? fallback : value;
    }

    /**
     * Renders one pipeline compile diagnostic as a readable sentence.
     *
     * @param diagnostic the diagnostic
     * @return the rendered text, never {@code null}
     */
    public final String renderDiagnostic(CompileDiagnostic diagnostic) {
        return diagnosticRenderer.render(diagnostic);
    }

    /**
     * Renders a pipeline reason key and its arguments as a readable sentence.
     *
     * @param reasonKey the language key
     * @param args placeholder arguments
     * @return the rendered text, never {@code null}
     */
    public final String renderDiagnostic(String reasonKey, Map<String, ?> args) {
        return diagnosticRenderer.render(reasonKey, args);
    }

    /**
     * Renders the first diagnostic of a line and notes how many others it has.
     *
     * @param diagnostics all diagnostics for one line
     * @return the rendered text, never {@code null}
     */
    public final String renderFirstDiagnostic(List<CompileDiagnostic> diagnostics) {
        return diagnosticRenderer.renderFirst(diagnostics);
    }

    public Component render(String text) {
        return MiniMessages.parse(text);
    }

    @Override
    public final void info(String key) {
        info(key, Map.of());
    }

    @Override
    public final void info(String key, Map<String, ?> replacements) {
        log(Level.INFO, message(key, replacements));
    }

    @Override
    public final void warning(String key) {
        warning(key, Map.of());
    }

    @Override
    public final void warning(String key, Map<String, ?> replacements) {
        log(Level.WARNING, message(key, replacements));
    }

    @Override
    public final void severe(String key) {
        severe(key, Map.of());
    }

    @Override
    public final void severe(String key, Map<String, ?> replacements) {
        log(Level.SEVERE, message(key, replacements));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, ?> replacements) {
        sendRaw(sender, message(key, replacements == null ? Map.of() : replacements));
    }

    public void sendRaw(CommandSender sender, String text) {
        sendPrefixed(sender, text);
    }

    public void sendComponent(CommandSender sender, Component component) {
        if (sender == null || component == null) {
            return;
        }
        sender.sendMessage(component);
    }

    protected String resolveMessage(String key) {
        if (messageResolver == null) {
            return key;
        }
        return messageResolver.apply(key);
    }

    protected String resolveMessage(String key, Map<String, ?> replacements) {
        if (replacementResolver != null) {
            return replacementResolver.apply(key, replacements);
        }
        return Texts.formatTemplate(resolveMessage(key), replacements);
    }

    protected boolean includePrefixInLogs() {
        return includePrefixInLogs;
    }

    protected final JavaPlugin plugin() {
        return plugin;
    }

    protected final void sendPrefixed(CommandSender sender, String text) {
        if (sender == null || Texts.isBlank(text)) {
            return;
        }
        sender.sendMessage(render(withPrefix(text)));
    }

    protected final String withPrefix(String text) {
        String prefix = message("general.prefix");
        if (Texts.isBlank(prefix) || "general.prefix".equals(prefix)) {
            prefix = defaultPrefix;
        }
        String normalizedText = Texts.toStringSafe(text);
        return prefix + (prefix.endsWith(" ") ? "" : " ") + normalizedText;
    }

    private void log(Level level, String text) {
        if (Texts.isBlank(text)) {
            return;
        }
        if (includePrefixInLogs()) {
            String loggedText = withPrefix(text);
            Bukkit.getConsoleSender().sendMessage(render(loggedText));
        } else {
            plugin.getLogger().log(level, MiniMessages.plain(render(text)));
        }
    }
}
