package emaki.jiuwu.craft.corelib.action.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.AfterGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.ChanceGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.source.SelfSource;
import emaki.jiuwu.craft.corelib.action.builtin.v2.stage.BroadcastMessageStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.stage.GivePotionEffectStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.stage.PlaySoundStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SendActionBarStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SendMessageStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SendTitleStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SpawnParticleStage;
import emaki.jiuwu.craft.corelib.action.v2.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.action.v2.compile.PipelineParser;
import emaki.jiuwu.craft.corelib.action.v2.compile.SequenceCatalog;
import emaki.jiuwu.craft.corelib.action.v2.compile.StageResolver;
import emaki.jiuwu.craft.corelib.action.v2.compile.StaticValidator;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/**
 * Compiles every line the converter would produce, using the real builtin stage metadata.
 *
 * <p>This is the safety valve the plan requires before any write: it proves the converter's output is
 * something the v2 engine accepts, rather than merely something that looks plausible in a diff. The
 * stage metadata comes from the actual builtin classes so a parameter rename cannot pass unnoticed.</p>
 *
 * <p>Temporary asset; removed with the rest of the phase 2 test assets.</p>
 */
class LegacyConvertedLinesCompileTest {

    private final PipelineParser parser = new PipelineParser();

    @Test
    @DisplayName("every converted line compiles against the real builtin stages")
    void convertedLinesCompile() throws IOException {
        StaticValidator validator = new StaticValidator(realResolver(), SequenceCatalog.empty(), null);
        LegacyFileScanner scanner = new LegacyFileScanner();
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<String> failures = new ArrayList<>();
        int compiled = 0;
        for (Path file : resourceYaml(root)) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (LegacyFileScanner.Change change : scanner.scan(content).changes()) {
                String line = change.newValue();
                PipelineParser.Result parsed = parser.parse(line);
                if (parsed.diagnostic() != null) {
                    failures.add(file.getFileName() + " L" + (change.lineIndex() + 1)
                            + " lex/parse: " + parsed.diagnostic().reasonKey() + " <- " + line);
                    continue;
                }
                StaticValidator.Result result = validator.validate(line, parsed.nodes(), null);
                if (result.pipeline() == null || !result.diagnostics().isEmpty()) {
                    List<String> keys = result.diagnostics().stream()
                            .map(CompileDiagnostic::reasonKey).toList();
                    failures.add(file.getFileName() + " L" + (change.lineIndex() + 1)
                            + " validate: " + keys + " <- " + line);
                    continue;
                }
                compiled++;
            }
        }
        System.out.println("compiled " + compiled + " converted line(s)");
        failures.forEach(System.out::println);
        // `combat.yml L22` (@chance=5, an invalid 500% that v1 also rejected) is tolerated because it is
        // a pre-existing config defect the converter passes through rather than inventing `5%` for.
        List<String> unexpected = failures.stream()
                .filter(failure -> !failure.startsWith("combat.yml L22 "))
                .toList();
        assertEquals(List.of(), unexpected, "every converted line must compile");
        // Zero is now the expected count: the shipped configs were migrated in phase 6b, so the scanner
        // finds nothing left to convert. The assertion is kept rather than dropped because it still
        // guards the real property — whatever the converter would produce has to compile — and it will
        // catch a regression that reintroduces legacy syntax into the shipped configs.
        assertEquals(0, compiled,
                "the shipped configs are already migrated, so nothing should need converting");
    }

    /** Builds a resolver from the real builtin stage declarations. */
    private StageResolver realResolver() {
        Map<String, CoreActionStage> actions = Map.of(
                "send_message", new SendMessageStage(),
                "send_action_bar", new SendActionBarStage(),
                "send_title", new SendTitleStage(),
                "broadcast_message", new BroadcastMessageStage(),
                "play_sound", new PlaySoundStage(),
                "spawn_particle", new SpawnParticleStage(),
                "give_potion_effect", new GivePotionEffectStage());
        Map<String, CoreActionGate> gates = Map.of(
                "chance", new ChanceGate(),
                "after", new AfterGate());
        // An omitted source segment compiles to an implicit `self`, so the validator resolves that id
        // even for lines that never name it. Without it every converted line fails on missing_self_source.
        Map<String, CoreActionSource> sources = Map.of("self", new SelfSource());
        return new StageResolver() {

            @Override
            public StageResolver.Resolution resolve(String id) {
                CoreActionStage action = actions.get(id);
                if (action != null) {
                    return StageResolver.Resolution.found(CoreStageKind.ACTION, action.parameters(),
                            action.requiredContext(), action.targetRequirement(),
                            ExecutionDomain.ENTITY);
                }
                CoreActionGate gate = gates.get(id);
                if (gate != null) {
                    return StageResolver.Resolution.found(CoreStageKind.GATE, gate.parameters(),
                            Set.of(), CoreTargetRequirement.NONE, ExecutionDomain.SERVER_GLOBAL);
                }
                CoreActionSource source = sources.get(id);
                if (source != null) {
                    return StageResolver.Resolution.found(CoreStageKind.SOURCE, source.parameters(),
                            Set.of(), CoreTargetRequirement.NONE, ExecutionDomain.SERVER_GLOBAL);
                }
                return StageResolver.Resolution.unknown();
            }

            @Override
            public List<String> knownIds(CoreStageKind kind) {
                return switch (kind) {
                    case ACTION -> List.copyOf(actions.keySet());
                    case GATE -> List.copyOf(gates.keySet());
                    case SOURCE -> List.copyOf(sources.keySet());
                };
            }
        };
    }

    private List<Path> resourceYaml(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .filter(path -> path.toString().replace('\\', '/')
                            .contains("/src/main/resources/"))
                    .filter(path -> !path.getParent().getFileName().toString().equals("lang"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }
}
