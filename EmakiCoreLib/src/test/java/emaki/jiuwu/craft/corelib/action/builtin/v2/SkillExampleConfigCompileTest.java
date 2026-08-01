package emaki.jiuwu.craft.corelib.action.builtin.v2;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Compiles the exact pipeline text shipped in the three EmakiSkills example skills.
 *
 * <p>Worth testing separately from {@link BuiltinPipelineCompileTest}: those lines are written to exercise the
 * stage table, these are the lines a server owner actually receives. A bundled example that does not compile
 * would fail at load time on a real server and is invisible in a build.</p>
 *
 * <p>Temporary asset for phase 4 verification.</p>
 */
class SkillExampleConfigCompileTest {

    private static ActionEngine engine() {
        StageRegistry registry = new StageRegistry();
        BuiltinStages.registerAll(registry, null, null, null, null, null, null, null, null, null, null);
        return new ActionEngine(
                new RegistryStageResolver(registry),
                new RegistryStageInvoker(registry),
                StageDispatcher.inline(),
                null,
                null);
    }

    private static void assertAllCompile(String phase, List<String> lines) {
        ActionEngine engine = engine();
        for (String line : lines) {
            ActionEngine.Result result = engine.compile(line, PhaseContract.permissive(phase));
            assertTrue(result.successful(),
                    () -> "example line should compile: " + line + " -> " + result.diagnostics());
            assertNotNull(result.pipeline());
        }
    }

    @Test
    void exampleSkillCompiles() {
        assertAllCompile("cast", List.of(
                "self | play_sound sound=entity.blaze.shoot volume=1 pitch=1.05",
                "self | spawn_particle particle=flame count=24 extra=0.02",
                "looking_at range=18 width=%radius% | keep"));
        assertAllCompile("hit", List.of(
                "inherited | damage amount=%damage%",
                "if %level%>=3 [ inherited | ignite duration=%ignite_ticks%t ]",
                "if %empowered%==true [ inherited | spawn_particle particle=explosion count=1 ]",
                "send_message text=\"%impact_message%\""));
        assertAllCompile("miss", List.of(
                "looking_at | spawn_particle particle=smoke count=8 extra=0.01",
                "send_message text=\"<gray>火球术没有命中目标。</gray>\""));
    }

    @Test
    void exampleComboSkillCompiles() {
        assertAllCompile("cast", List.of(
                "nearby radius=4 limit=20 | damage amount=20",
                "inherited | spawn_particle particle=explosion count=5",
                "self | play_sound sound=minecraft:entity.generic.explode volume=1 pitch=0.8",
                "send_message text='<red>连击终结！</red>'"));
    }

    @Test
    void exampleProjectileSkillCompiles() {
        assertAllCompile("cast", List.of(
                "self | projectile speed=1.2 gravity=0 lifetime=80 hit_radius=0.8 pierce=0 "
                        + "homing=true homing_strength=0.15 particle=flame damage=15 direction=look",
                "looking_at range=20 | keep"));
        assertAllCompile("hit", List.of(
                "nearby radius=3 limit=20 | damage amount=8",
                "inherited | spawn_particle particle=explosion count=3",
                "inherited | play_sound sound=minecraft:entity.generic.explode volume=0.8 pitch=1.2"));
    }
}
