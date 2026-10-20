package emaki.jiuwu.craft.dungeon.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;

class OptionSealTest {

    @TempDir
    Path tempDir;

    private static final String FLOW_YAML = """
            options:
              opt_fork:
                option_id: opt_fork
                select_mode: multi
                min_select: 1
                max_select: 2
                time_limit_seconds: 30
                location:
                  x: 1.5
                  y: 64.0
                  z: -2.5
                  yaw: 90.0
                  pitch: 0.0
                cases:
                  case_a:
                    label: 左路
                    case: a
                    ending: end_a
                    seal_regions:
                      - "min.x": 0.0
                        "min.y": 0.0
                        "min.z": 0.0
                        "max.x": 5.0
                        "max.y": 10.0
                        "max.z": 5.0
                  case_b:
                    label: 右路
                    case: b
                    ending: end_b
                    seal_regions:
                      - "min.x": 10.0
                        "min.y": 0.0
                        "min.z": 0.0
                        "max.x": 15.0
                        "max.y": 10.0
                        "max.z": 5.0
                  case_c:
                    label: 中路
                    case: c
                    ending: end_c
                safe-point:
                  x: 0.0
                  y: 65.0
                  z: 0.0
                ending_map:
                  a: end_hidden
            """;

    private FlowDefinition load() throws IOException {
        File flowFile = tempDir.resolve("flow.yml").toFile();
        Files.writeString(flowFile.toPath(), FLOW_YAML, StandardCharsets.UTF_8);
        return new FlowLoader().load(flowFile);
    }

    @Test
    void optionDefinitionParsesSelectModeBoundsAndCases() throws IOException {
        FlowDefinition definition = load();
        FlowModules.OptionDef option = definition.options().get("opt_fork");
        assertNotNull(option);
        assertEquals("multi", option.selectMode());
        assertEquals(1, option.minSelect());
        assertEquals(2, option.maxSelect());
        assertEquals(30, option.timeLimitSeconds());
        assertEquals(3, option.cases().size());
        assertNotNull(option.point());
        assertEquals(1.5D, option.point().x(), 0.001D);
        assertNotNull(option.safePoint());
        assertEquals(65.0D, option.safePoint().y(), 0.001D);
        assertEquals("end_hidden", option.endingMap().get("a"));
    }

    @Test
    void sealRegionsParseFromCaseDefinition() throws IOException {
        FlowDefinition definition = load();
        FlowModules.OptionDef option = definition.options().get("opt_fork");
        FlowModules.OptionCase caseA = option.cases().stream()
                .filter(entry -> "a".equals(entry.caseId()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, caseA.sealRegions().size());
        FlowRegion region = caseA.sealRegions().get(0);
        assertEquals(0.0D, region.minX(), 0.001D);
        assertEquals(5.0D, region.maxX(), 0.001D);
        assertEquals(10.0D, region.maxY(), 0.001D);
        FlowModules.OptionCase caseC = option.cases().stream()
                .filter(entry -> "c".equals(entry.caseId()))
                .findFirst()
                .orElseThrow();
        assertTrue(caseC.sealRegions().isEmpty());
    }

    @Test
    void sealedCasesAreExactlyTheUnpickedOnes() throws IOException {
        FlowDefinition definition = load();
        FlowModules.OptionDef option = definition.options().get("opt_fork");
        List<FlowModules.OptionCase> sealed =
                GameplayControllers.sealedCases(option, Set.of("a", "c"));
        assertEquals(1, sealed.size());
        assertEquals("b", sealed.get(0).caseId());

        List<FlowModules.OptionCase> noneSealed =
                GameplayControllers.sealedCases(option, Set.of("a", "b", "c"));
        assertTrue(noneSealed.isEmpty());

        List<FlowModules.OptionCase> allSealed =
                GameplayControllers.sealedCases(option, Set.of());
        assertEquals(3, allSealed.size());
    }

    @Test
    void singlePickSealsEveryOtherCase() throws IOException {
        FlowDefinition definition = load();
        FlowModules.OptionDef option = definition.options().get("opt_fork");
        List<FlowModules.OptionCase> sealed =
                GameplayControllers.sealedCases(option, Set.of("b"));
        assertEquals(2, sealed.size());
        assertNull(sealed.stream().filter(entry -> "b".equals(entry.caseId())).findFirst().orElse(null));
    }

    @Test
    void confirmedPickLocksOptionCaseIntoRuntimeFacts() throws IOException {
        org.mockbukkit.mockbukkit.MockBukkit.mock();
        try {
            FlowDefinition definition = load();
            DungeonInstance instance = new DungeonInstance("emd_seal", "demo",
                    new emaki.jiuwu.craft.dungeon.instance.WorldCommandQueue(total -> {
                    }));
            var messages = new emaki.jiuwu.craft.corelib.service.MessageService(
                    org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin("EmakiDungeonTest"),
                    new emaki.jiuwu.craft.corelib.loader.LanguageLoader(
                            org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin("EmakiDungeonTest")),
                    "EmakiDungeon");
            FlowRuntime runtime = new FlowRuntime(instance, definition,
                    new Adjudicator(new ConditionEvaluator(), new TriggerCounter()),
                    messages, playerId -> {
                    });
            assertNull(runtime.fact("option_case:opt_fork"));
            runtime.setFact("option_case:opt_fork", "a");
            assertEquals("a", runtime.fact("option_case:opt_fork"));
            runtime.setFact("option_case:opt_fork", "a");
            assertEquals("a", runtime.fact("option_case:opt_fork"));
        } finally {
            org.mockbukkit.mockbukkit.MockBukkit.unmock();
        }
    }
}
