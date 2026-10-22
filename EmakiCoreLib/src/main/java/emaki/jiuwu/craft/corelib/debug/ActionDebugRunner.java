package emaki.jiuwu.craft.corelib.debug;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.ActionEngine;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.service.AbstractMessageService;

public final class ActionDebugRunner {

    public static final String[] MODES = {"run", "check", "registry"};

    public static final String[] RUN_MODES = {"shadow", "execute"};

    private ActionDebugRunner() {
    }

    public static void registry(@Nullable emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry registry,
            MessageWriter writer) {
        if (registry == null) {
            writer.send("command.action_unavailable");
            return;
        }
        java.util.Map<emaki.jiuwu.craft.corelib.api.action.CoreStageKind, List<String>> byKind =
                registry.allIds();
        int total = byKind.values().stream().mapToInt(List::size).sum();
        writer.send("command.action_list_header", Map.of("count", String.valueOf(total)));
        if (total == 0) {
            writer.send("command.action_list_empty");
            return;
        }
        for (emaki.jiuwu.craft.corelib.api.action.CoreStageKind kind
                : emaki.jiuwu.craft.corelib.api.action.CoreStageKind.values()) {
            for (String id : byKind.getOrDefault(kind, List.of())) {
                writer.send("command.action_list_item", Map.of(
                        "id", id,
                        "kind", Texts.lower(kind.name()),
                        "owner", ownerNameOf(registry, kind, id)));
            }
        }
    }

    private static String ownerNameOf(
            emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry registry,
            emaki.jiuwu.craft.corelib.api.action.CoreStageKind kind,
            String id) {
        String owner = switch (kind) {
            case SOURCE -> registry.sources().ownerNameOf(id);
            case GATE -> registry.gates().ownerNameOf(id);
            case ACTION -> registry.actions().ownerNameOf(id);
        };
        return Texts.isBlank(owner) ? "-" : owner;
    }

    public static void check(@Nullable ActionEngine engine,
            CommandSender sender,
            String rawLine,
            MessageWriter writer) {
        if (engine == null) {
            writer.send("command.action_unavailable");
            return;
        }
        if (Texts.isBlank(rawLine)) {
            writer.send("command.action_debug_usage");
            return;
        }
        ActionEngine.Result compiled = engine.compile(rawLine, null);
        if (compiled.successful()) {
            writer.send("command.action_debug_check_ok", Map.of("line", rawLine));
            return;
        }
        writer.send("command.action_debug_check_failed_header", Map.of(
                "line", rawLine,
                "count", String.valueOf(compiled.diagnostics().size())));
        for (CompileDiagnostic diagnostic : compiled.diagnostics()) {
            writer.send("command.action_debug_check_item", Map.of(
                    "reason", writer.render(diagnostic)));
        }
    }

    public static void run(@Nullable ActionEngine engine,
            Plugin owner,
            @Nullable Player caster,
            CommandSender sender,
            String rawLine,
            boolean dryRun,
            MessageWriter writer,
            Consumer<Runnable> dispatch) {
        if (engine == null) {
            writer.send("command.action_unavailable");
            return;
        }
        if (Texts.isBlank(rawLine)) {
            writer.send("command.action_debug_usage");
            return;
        }
        PipelineContext context = PipelineContext.root(owner,
                caster == null
                        ? emaki.jiuwu.craft.corelib.api.action.CoreActionSubject.absent()
                        : emaki.jiuwu.craft.corelib.api.action.CoreActionSubject.of(caster),
                null, "command.action_debug", false,
                emaki.jiuwu.craft.corelib.action.pipeline.PlaceholderBridge.noop())
                .withVariables(Map.of(
                        "sender", sender.getName(),
                        "player", caster == null ? "" : caster.getName(),
                        "action_line", rawLine));
        writer.send(dryRun
                        ? "command.action_debug_dry_run_header"
                        : "command.action_debug_execute_header",
                Map.of("line", rawLine));
        ChatStepReporter reporter = new ChatStepReporter(writer::write,
                line -> dispatch.accept(() -> writer.sendText(line)));
        ActionEngine derived = engine.withReporter(reporter, dryRun);
        PhaseContract phase = PhaseContract.declared(context.phase(), Set.copyOf(context.presentKeys()),
                context.variables().keySet(), !context.targets().isEmpty());
        ActionEngine.Result compiled = derived.compile(rawLine, phase);
        if (!compiled.successful() || compiled.pipeline() == null) {
            writer.send("command.action_compile_failed", Map.of(
                    "line", rawLine,
                    "reason", writer.renderFirst(compiled.diagnostics())));
            return;
        }
        derived.run(owner, compiled.pipeline(), context).whenComplete((outcome, throwable) -> dispatch.accept(() -> {
            if (throwable != null) {
                writer.send("command.action_debug_summary", Map.of("status", "error", "stages", "0"));
                return;
            }
            if (outcome == null) {
                writer.send("command.action_debug_summary", Map.of("status", "-", "stages", "0"));
                return;
            }
            writer.send("command.action_debug_summary", Map.of(
                    "status", Texts.lower(outcome.status().name()),
                    "stages", String.valueOf(outcome.stageResults().size())));
        }));
    }

    public static @Nullable Invocation parse(String[] args) {
        String mode = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        if ("check".equals(mode)) {
            return new Invocation(Mode.CHECK, joinTail(args, 1));
        }
        if ("registry".equals(mode)) {
            return new Invocation(Mode.REGISTRY, "");
        }
        if ("run".equals(mode)) {
            String runMode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "shadow";
            if (!"shadow".equals(runMode) && !"execute".equals(runMode)) {
                return null;
            }
            return new Invocation("execute".equals(runMode) ? Mode.EXECUTE : Mode.SHADOW,
                    joinTail(args, 2));
        }
        return null;
    }

    public enum Mode {
        CHECK,
        REGISTRY,
        SHADOW,
        EXECUTE
    }

    public record Invocation(@NotNull Mode mode, String rawLine) {
    }

    private static String joinTail(String[] args, int startIndex) {
        if (args == null || startIndex >= args.length) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = startIndex; index < args.length; index++) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    public interface MessageWriter {

        void send(String key);

        void send(String key, Map<String, ?> replacements);

        void sendText(String line);

        String write(String key, Map<String, ?> replacements);

        String render(CompileDiagnostic diagnostic);

        String renderFirst(List<CompileDiagnostic> diagnostics);

        static MessageWriter of(AbstractMessageService messageService, CommandSender sender) {
            return new MessageWriter() {

                @Override
                public void send(String key) {
                    messageService.sendRaw(sender, messageService.message(key));
                }

                @Override
                public void send(String key, Map<String, ?> replacements) {
                    messageService.sendRaw(sender, messageService.message(key, replacements));
                }

                @Override
                public void sendText(String line) {
                    messageService.sendRaw(sender, line);
                }

                @Override
                public String write(String key, Map<String, ?> replacements) {
                    return messageService.message(key, replacements);
                }

                @Override
                public String render(CompileDiagnostic diagnostic) {
                    return messageService.renderDiagnostic(diagnostic);
                }

                @Override
                public String renderFirst(List<CompileDiagnostic> diagnostics) {
                    return messageService.renderFirstDiagnostic(diagnostics);
                }
            };
        }
    }
}
