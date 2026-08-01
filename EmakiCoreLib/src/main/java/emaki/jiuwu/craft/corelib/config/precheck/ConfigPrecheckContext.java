package emaki.jiuwu.craft.corelib.config.precheck;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.v2.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.action.v2.compile.PipelineLimits;
import emaki.jiuwu.craft.corelib.action.v2.compile.PipelineParser;
import emaki.jiuwu.craft.corelib.action.v2.compile.SequenceCatalog;
import emaki.jiuwu.craft.corelib.action.v2.compile.StageResolver;
import emaki.jiuwu.craft.corelib.action.v2.compile.StaticValidator;
import emaki.jiuwu.craft.corelib.action.v2.exec.ConfiguredSequenceRepository;
import emaki.jiuwu.craft.corelib.action.v2.registry.RegistryStageResolver;
import emaki.jiuwu.craft.corelib.action.v2.registry.StageRegistry;

/**
 * What a precheck contributor may ask about configured pipeline lines.
 *
 * <p>Contributors get one capability rather than the compiler's parts: a line either compiles or it
 * produces diagnostics. Handing out the resolver, the parser and the catalog separately would let every
 * module re-invent its own idea of "valid", which is how the v1 prechecks drifted away from what the
 * executor actually accepted.</p>
 *
 * <p>The verdict here is the engine's own verdict: the same parser, validator, sequence catalog and
 * permissive phase contract the runtime uses when it compiles the very same lines. So a line this context
 * rejects is a line that would have been rejected at load time anyway, only now it is reported before the
 * server finishes starting.</p>
 */
public final class ConfigPrecheckContext {

    private final PipelineParser parser = new PipelineParser();
    private final StaticValidator validator;
    private final boolean compilable;

    private ConfigPrecheckContext(@Nullable StageResolver stages,
            @Nullable SequenceCatalog sequences,
            @Nullable PipelineLimits limits) {
        this.compilable = stages != null;
        this.validator = new StaticValidator(stages, sequences, limits);
    }

    /**
     * Creates a context bound to one stage table and one set of sequence definitions.
     *
     * @param registry the stage table to validate against, {@code null} before the action system is built
     * @param sequenceDefinitions configured sequence name to its raw pipeline lines
     * @param limits compile limits taken from configuration
     * @return the context
     */
    public static @NotNull ConfigPrecheckContext of(@Nullable StageRegistry registry,
            @Nullable Map<String, List<String>> sequenceDefinitions,
            @Nullable PipelineLimits limits) {
        StageResolver resolver = registry == null ? null : new RegistryStageResolver(registry);
        // Only names, required parameters and outgoing calls are needed here, and build() derives those from
        // the raw text in its first pass. Nothing is executed during a precheck, so no compiled body is
        // requested; asking for one would mean compiling every sequence twice.
        SequenceCatalog catalog = ConfiguredSequenceRepository.build(sequenceDefinitions,
                (sequence, line, self) -> null);
        return new ConfigPrecheckContext(resolver, catalog, limits);
    }

    /**
     * Reports whether pipeline lines can be judged at all.
     *
     * <p>False before the stage table exists. A contributor must then skip its pipeline checks rather than
     * treat every stage as unknown, because an empty stage table would turn a correct configuration into a
     * wall of errors.</p>
     *
     * @return whether {@link #compileDiagnostics(String)} can produce a meaningful verdict
     */
    public boolean canCompile() {
        return compilable;
    }

    /**
     * Compiles one pipeline line and reports what is wrong with it.
     *
     * <p>Blank lines and comments yield no diagnostics: configuration authors use them for spacing, and the
     * runtime skips them the same way.</p>
     *
     * @param line the pipeline text as written in configuration
     * @return every problem found, empty when the line compiles or when {@link #canCompile()} is false
     */
    public @NotNull List<CompileDiagnostic> compileDiagnostics(@Nullable String line) {
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
        // A permissive phase contract on purpose: a configured sequence has no single trigger, so the
        // context keys available to it are only known where it is called from.
        return validator.validate(line, parsed.nodes(), null).diagnostics();
    }
}
