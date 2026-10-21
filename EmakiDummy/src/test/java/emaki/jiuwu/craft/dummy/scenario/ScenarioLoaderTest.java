package emaki.jiuwu.craft.dummy.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScenarioLoaderTest {

    private static final String VALID_YAML = """
            players:
              - dummy_a
              - dummy_b
            timeout_seconds: 120
            steps:
              - action: join_all
              - action: cmd
                player: dummy_a
                command: "/edg enter test_dungeon"
              - action: wait_event
                type: DungeonEnterEvent
                timeout: 60
              - action: expect
                expr: "online_count == 2"
                timeout: 30
              - action: assert
                expr: "audit_contains('event=complete')"
                on_fail: continue
              - action: leave_all
            """;

    @Test
    void parsesPlayersTimeoutAndStepParameters() throws ScenarioLoadException {
        ScenarioDefinition definition = ScenarioLoader.parse(VALID_YAML, "sample");
        assertEquals("sample", definition.id());
        assertEquals(2, definition.players().size());
        assertEquals(120, definition.timeoutSeconds());
        assertEquals(6, definition.steps().size());

        ScenarioStep commandStep = definition.steps().get(1);
        assertEquals("cmd", commandStep.action());
        assertEquals("dummy_a", commandStep.param("player"));
        assertEquals("/edg enter test_dungeon", commandStep.param("command"));

        ScenarioStep eventStep = definition.steps().get(2);
        assertEquals("DungeonEnterEvent", eventStep.param("type"));
        assertEquals(60, eventStep.timeoutSeconds(99));

        ScenarioStep assertStep = definition.steps().get(4);
        assertTrue(assertStep.continueOnFail());
        assertEquals(99, assertStep.timeoutSeconds(99));
    }

    @Test
    void rejectsUnknownAction() {
        String yaml = """
                players:
                  - dummy_a
                steps:
                  - action: teleport
                """;
        ScenarioLoadException failure = assertThrows(ScenarioLoadException.class,
                () -> ScenarioLoader.parse(yaml, "bad"));
        assertTrue(failure.getMessage().contains("unknown action"));
    }

    @Test
    void rejectsPlayerOutsideDeclaredList() {
        String yaml = """
                players:
                  - dummy_a
                steps:
                  - action: say
                    player: dummy_x
                    text: "hi"
                """;
        ScenarioLoadException failure = assertThrows(ScenarioLoadException.class,
                () -> ScenarioLoader.parse(yaml, "bad"));
        assertTrue(failure.getMessage().contains("not declared"));
    }

    @Test
    void rejectsMissingRequiredParam() {
        String yaml = """
                players:
                  - dummy_a
                steps:
                  - action: cmd
                    player: dummy_a
                """;
        ScenarioLoadException failure = assertThrows(ScenarioLoadException.class,
                () -> ScenarioLoader.parse(yaml, "bad"));
        assertTrue(failure.getMessage().contains("requires command"));
    }

    @Test
    void rejectsUnknownParamAndEmptyPlayers() {
        String unknownParam = """
                players:
                  - dummy_a
                steps:
                  - action: join_all
                    speed: 2
                """;
        ScenarioLoadException paramFailure = assertThrows(ScenarioLoadException.class,
                () -> ScenarioLoader.parse(unknownParam, "bad"));
        assertTrue(paramFailure.getMessage().contains("unknown param"));

        String noPlayers = """
                steps:
                  - action: join_all
                """;
        ScenarioLoadException playersFailure = assertThrows(ScenarioLoadException.class,
                () -> ScenarioLoader.parse(noPlayers, "bad"));
        assertTrue(playersFailure.getMessage().contains("players"));
    }

    @Test
    void parsesClickStepAndRequiresSlot() throws ScenarioLoadException {
        String valid = """
                players:
                  - dummy_a
                steps:
                  - action: click
                    player: dummy_a
                    slot: 0
                """;
        ScenarioDefinition definition = ScenarioLoader.parse(valid, "click_ok");
        assertEquals("click", definition.steps().get(0).action());
        assertEquals(0, definition.steps().get(0).numberParam("slot").intValue());

        String missingSlot = """
                players:
                  - dummy_a
                steps:
                  - action: click
                    player: dummy_a
                """;
        ScenarioLoadException failure = assertThrows(ScenarioLoadException.class,
                () -> ScenarioLoader.parse(missingSlot, "bad"));
        assertTrue(failure.getMessage().contains("requires slot"));
    }
}
