package emaki.jiuwu.craft.corelib.action.builtin.v2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.action.v2.ActionEngine;
import emaki.jiuwu.craft.corelib.action.v2.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.action.v2.exec.RegistryStageInvoker;
import emaki.jiuwu.craft.corelib.action.v2.exec.StageDispatcher;
import emaki.jiuwu.craft.corelib.action.v2.registry.RegistryStageResolver;
import emaki.jiuwu.craft.corelib.action.v2.registry.StageRegistry;

/**
 * Compiles real pipeline text against the registered builtin stage table.
 *
 * <p>This is the check that matters most for phase 3: the contract tests prove the stages registered, but only
 * compilation proves a server owner can actually write them. Every line here is one a configuration would
 * plausibly contain.</p>
 *
 * <p>Temporary asset for phase 3 verification.</p>
 */
class BuiltinPipelineCompileTest {

    private static ActionEngine engine() {
        StageRegistry registry = new StageRegistry();
        BuiltinStages.registerAll(registry, null, null, null, null, null, null, null);
        return new ActionEngine(
                new RegistryStageResolver(registry),
                new RegistryStageInvoker(registry),
                StageDispatcher.inline(),
                null,
                null);
    }

    private static void assertCompiles(ActionEngine engine, String line) {
        ActionEngine.Result result = engine.compile(line, PhaseContract.permissive("test"));
        assertTrue(result.successful(),
                () -> "should compile: " + line + " -> " + describe(result));
        assertNotNull(result.pipeline());
    }

    private static void assertRejected(ActionEngine engine, String line) {
        ActionEngine.Result result = engine.compile(line, PhaseContract.permissive("test"));
        assertFalse(result.successful(), () -> "should be rejected: " + line);
        assertNull(result.pipeline());
    }

    private static String describe(ActionEngine.Result result) {
        StringBuilder builder = new StringBuilder();
        result.diagnostics().forEach(diagnostic -> builder
                .append(diagnostic.reasonKey()).append('(').append(diagnostic.token()).append(") "));
        return builder.toString();
    }

    @Test
    void compilesTheOmittedSourceForm() {
        // Decision Q4: no source means self, so the shortest useful line is just an action.
        assertCompiles(engine(), "send_message text=\"<green>hello\"");
    }

    @Test
    void compilesEverySourceStage() {
        ActionEngine engine = engine();
        for (String line : List.of(
                "self | send_message text=hi",
                "inherited | send_message text=hi",
                "trigger | send_message text=hi",
                "origin | spawn_particle particle=flame",
                "looking_at range=18 | damage amount=5",
                "looking_at range=18 width=2 | damage amount=5",
                "nearby radius=5 limit=3 type=zombie | kill_entity",
                "nearby radius=5 include_players=true | damage amount=2",
                "nearby_players radius=10 | send_message text=hi",
                "offset x=1 y=2 z=3 | spawn_particle particle=flame",
                "offset z=3 relative=true | spawn_particle particle=flame",
                "at world=world x=10 y=64 z=10 | set_block material=stone",
                "at x=~ y=~5 z=~ | spawn_particle particle=flame",
                "player_by_name Notch | send_message text=hi")) {
            assertCompiles(engine, line);
        }
    }

    @Test
    void compilesEveryGateStage() {
        ActionEngine engine = engine();
        for (String line : List.of(
                "self | where 3>2 | send_message text=hi",
                "chance 50% | self | send_message text=hi",
                "nearby radius=5 | limit 2 | damage amount=1",
                "nearby radius=5 | sort_by distance | damage amount=1",
                "nearby radius=5 | sort_by health order=desc | damage amount=1",
                "self | set damage=4*4+2 | damage amount=%var.damage%",
                "looking_at range=18 | keep",
                "self | stop")) {
            assertCompiles(engine, line);
        }
    }

    @Test
    void compilesTimingStages() {
        ActionEngine engine = engine();
        assertCompiles(engine, "after 10t | self | send_message text=hi");
        assertCompiles(engine, "every 20t times 5 | self | spawn_particle particle=flame");
        assertCompiles(engine, "after 500ms | self | send_message text=hi");
        assertCompiles(engine, "after 2s | self | send_message text=hi");
    }

    @Test
    void compilesBranches() {
        ActionEngine engine = engine();
        assertCompiles(engine, "self | if 3>2 [ send_message text=yes ] else [ send_message text=no ]");
        assertCompiles(engine, "self | if 3>2 [ send_message text=yes ]");
        assertCompiles(engine, "self | if 3>2 [ if 1<2 [ send_message text=deep ] ]");
    }

    @Test
    void compilesTheItemHandoff() {
        // create_item publishes the typed item key and send_item declares that it needs it. This line is the
        // reason create_item had to be a gate: only gates feed typed context back into the pipeline.
        assertCompiles(engine(), "create_item item_source=stone amount=4 | self | send_item");
    }

    @Test
    void compilesRepresentativeActionStages() {
        ActionEngine engine = engine();
        for (String line : List.of(
                "self | send_action_bar text=hi",
                "self | send_title title=Big subtitle=Small fade_in=10t stay=40t fade_out=10t",
                "broadcast_message text=hi",
                "self | play_sound sound=entity.player.levelup volume=1 pitch=1.2",
                "origin | spawn_particle particle=flame count=20 offset_x=0.5",
                "self | boss_bar_show id=phase title=Boss progress=0.5 color=red style=segmented_10",
                "self | boss_bar_hide id=phase",
                "self | heal amount=10",
                "self | damage amount=3",
                "self | set_health amount=20",
                "self | feed amount=20 saturation=5",
                "self | ignite duration=5s",
                "self | extinguish",
                "self | give_potion_effect type=speed level=2 duration=30s",
                "self | remove_potion_effect type=speed",
                "self | clear_potion_effects",
                "self | give_item item_source=diamond amount=2",
                "self | set_item slot=mainhand item_source=stone",
                "self | clear_item slot=offhand",
                "self | take_item item_source=stone amount=8",
                "origin | drop_item item_source=stone amount=1",
                "self | repair_item slot=mainhand",
                "self | damage_item slot=mainhand amount=5 delete_item=true",
                "at x=0 y=64 z=0 | place_block item_source=stone",
                "at x=0 y=64 z=0 | set_block block_data=\"minecraft:oak_stairs[facing=east]\"",
                "at x=0 y=64 z=0 | break_block drop_items=true",
                "at x=0 y=64 z=0 | explosion power=2 fire=false break_blocks=false",
                "at x=0 y=64 z=0 | spawn_entity type=zombie count=2",
                "self | teleport x=~ y=~10 z=~",
                "self | give_money amount=100",
                "self | take_money amount=50 provider=vault",
                "self | set_money amount=0 currency=coins",
                "self | give_exp amount=30",
                "self | take_exp amount=1 mode=levels",
                "self | set_exp amount=0",
                "self | run_command_as_player command=spawn",
                "self | run_command_as_op command=\"/give @s stone\"",
                "run_command_as_console command=\"save-all\"",
                "self | cast_mythic_skill skill=Fireball")) {
            assertCompiles(engine, line);
        }
    }

    @Test
    void rejectsAnUnknownStageId() {
        assertRejected(engine(), "self | sendmessage text=hi");
    }

    @Test
    void rejectsAMisspelledArgument() {
        // The point of declaring parameters: a typo is a load error rather than a silently ignored argument.
        assertRejected(engine(), "self | heal ammount=5");
    }

    @Test
    void rejectsAMissingRequiredArgument() {
        assertRejected(engine(), "self | send_message");
    }

    @Test
    void rejectsTwoSourcesInOnePipeline() {
        assertRejected(engine(), "self | nearby radius=5 | damage amount=1");
    }

    @Test
    void rejectsASourceAfterAnAction() {
        assertRejected(engine(), "self | send_message text=hi | nearby radius=5");
    }

    @Test
    void rejectsARepeatCountOverTheLimit() {
        // Decision D4: exceeding the cap rejects the configuration instead of silently truncating it.
        assertRejected(engine(), "every 20t times 100000 | self | send_message text=hi");
    }

    @Test
    void allowsSetToNameItsOwnVariables() {
        // set is the one stage exempt from the unknown-argument check, since its keys are author-chosen.
        ActionEngine engine = engine();
        assertCompiles(engine, "self | set anything_at_all=1 | send_message text=%var.anything_at_all%");
        assertCompiles(engine, "self | set a=1 b=2 c=3 | send_message text=hi");
    }

    @Test
    void keepsTheUnknownArgumentCheckForEveryOtherStage() {
        // Proves the set exemption is not a hole: the next stage in the same line still gets checked.
        assertRejected(engine(), "self | set a=1 | send_message txt=hi");
    }
}
