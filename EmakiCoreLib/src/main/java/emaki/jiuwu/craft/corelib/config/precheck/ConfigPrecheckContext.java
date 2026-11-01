package emaki.jiuwu.craft.corelib.config.precheck;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.PipelineLimits;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.PipelineParser;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.SequenceCatalog;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.StageResolver;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.StaticValidator;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.ConfiguredSequenceRepository;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.RegistryStageResolver;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;

public final class ConfigPrecheckContext {

    private final PipelineParser parser = new PipelineParser();
    private final StaticValidator validator;
    private final boolean compilable;
    private final StageRegistry registry;

    private ConfigPrecheckContext(@Nullable StageResolver stages,
            @Nullable SequenceCatalog sequences,
            @Nullable PipelineLimits limits,
            @Nullable StageRegistry registry) {
        this.compilable = stages != null;
        this.validator = new StaticValidator(stages, sequences, limits);
        this.registry = registry;
    }

    public static @NotNull ConfigPrecheckContext of(@Nullable StageRegistry registry,
            @Nullable Map<String, List<String>> sequenceDefinitions,
            @Nullable PipelineLimits limits) {
        StageResolver resolver = registry == null ? null : new RegistryStageResolver(registry);

        SequenceCatalog catalog = ConfiguredSequenceRepository.build(sequenceDefinitions,
                (sequence, line, self) -> null);
        return new ConfigPrecheckContext(resolver, catalog, limits, registry);
    }

    public boolean knowsSource(@Nullable String id) {
        return registry != null && registry.kindOf(id) == CoreStageKind.SOURCE;
    }

    public boolean canCompile() {
        return compilable;
    }

    public @NotNull List<CompileDiagnostic> compileDiagnostics(@Nullable String line) {
        return compileDiagnostics(line, null);
    }

    public @NotNull List<CompileDiagnostic> compileDiagnostics(@Nullable String line,
            @Nullable PhaseContract phase) {
        if (!compilable) {
            return List.of();
        }
        PipelineParser.Result parsed = parser.parse(line);
        if (parsed.blank()) {
            return List.of();
        }
        if (parsed.diagnostic() != null) {
            return List.of(parsed.diagnostic());
        }

        return validator.validate(line, parsed.nodes(), phase).diagnostics();
    }
}
