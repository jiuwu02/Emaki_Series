package emaki.jiuwu.craft.corelib.script.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.script.host.ScriptHost;
import emaki.jiuwu.craft.corelib.script.host.ScriptHostSettings;

@DisplayName("动作段绑定桥 payload 校验与注册")
class ActionStagePayloadTest {

    @TempDir
    Path dataDirectory;

    private ScriptHost host;
    private StageRegistry stageRegistry;
    private final List<String> errors = new ArrayList<>();
    private final List<ScriptActionStage> stages = new ArrayList<>();
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (host != null) {
            host.close();
            host = null;
        }
        for (CoreStageRegistration handle : handles) {
            handle.close();
        }
        handles.clear();
        stages.clear();
        errors.clear();
    }

    private void loadScripts(Map<String, String> files) throws IOException {
        Path scripts = dataDirectory.resolve("scripts");
        Files.createDirectories(scripts);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Files.writeString(scripts.resolve(entry.getKey()), entry.getValue());
        }
        stageRegistry = new StageRegistry();
        ActionStageScriptBinding binding = new ActionStageScriptBinding(
                () -> stageRegistry,
                null,
                () -> host == null ? null : host.callbackRunner(),
                5000L,
                reason -> reason,
                errors::add,
                stages,
                handles);
        host = new ScriptHost(null, scripts, new ScriptHostSettings(true, 500, false),
                Map.of("actions", binding));
        host.load();
    }

    @Test
    @DisplayName("合法 payload 映射为 CoreActionStage 并注册成功")
    void validPayloadMapsToStageAndRegisters() throws IOException {
        loadScripts(Map.of("demo.js", """
                actions.register({
                    id: 'sc_stage_demo',
                    description: 'demo stage',
                    category: 'custom',
                    parameters: [
                        { name: 'Amount', type: 'double', required: true, default: '', description: 'amount' },
                        { name: 'flag', type: 'boolean', required: false, default: 'true', description: 'flag' }
                    ],
                    timeout: 1234,
                    execute: function (ctx, args) { return 1; }
                });
                """));
        assertTrue(errors.isEmpty(), () -> "unexpected errors: " + errors);
        assertEquals(1, stages.size());
        assertEquals(List.of("sc_stage_demo"), stageRegistry.actions().ids());
        ScriptActionStage stage = stages.getFirst();
        assertEquals("sc_stage_demo", stage.id());
        assertEquals("demo stage", stage.description());
        assertEquals("custom", stage.category());
        assertEquals(1234L, stage.timeoutMillis());
        assertEquals(CoreTargetRequirement.NONE, stage.targetRequirement());
        assertEquals(CoreActionExecutionDomain.ASYNC_COMPUTE,
                stage.executionTarget(CoreStagePlanningContext.probe()).domain());
        assertEquals(2, stage.parameters().size());
        assertEquals("amount", stage.parameters().get(0).name());
        assertEquals(CoreStageParameterType.DOUBLE, stage.parameters().get(0).type());
        assertTrue(stage.parameters().get(0).required());
        assertEquals("flag", stage.parameters().get(1).name());
        assertEquals(CoreStageParameterType.BOOLEAN, stage.parameters().get(1).type());
        assertFalse(stage.parameters().get(1).required());
        assertEquals("true", stage.parameters().get(1).defaultValue());
        assertEquals(CoreStageKind.ACTION, handles.getFirst().kind());
    }

    @Test
    @DisplayName("缺省字段取默认值：category=script、timeout 取 settings")
    void defaultsAreApplied() throws IOException {
        loadScripts(Map.of("defaults.js", """
                actions.register({
                    id: 'sc_stage_defaults',
                    execute: function (ctx, args) { return 1; }
                });
                """));
        assertTrue(errors.isEmpty(), () -> "unexpected errors: " + errors);
        assertEquals(1, stages.size());
        assertEquals("script", stages.getFirst().category());
        assertEquals(5000L, stages.getFirst().timeoutMillis());
        assertEquals(0, stages.getFirst().parameters().size());
    }

    @Test
    @DisplayName("非法 type、缺失 execute、空 id 与非法 timeout 被显式拒绝")
    void invalidPayloadsAreRejectedExplicitly() throws IOException {
        loadScripts(Map.of(
                "a_bad_type.js", "actions.register({id: 'sc_stage_bad_type', "
                        + "parameters: [{name: 'amount', type: 'nope'}], execute: function () { return 1; }});",
                "b_no_execute.js", "actions.register({id: 'sc_stage_no_execute'});",
                "c_no_id.js", "actions.register({execute: function () { return 1; }});",
                "d_bad_timeout.js", "actions.register({id: 'sc_stage_bad_timeout', "
                        + "timeout: 'soon', execute: function () { return 1; }});"));
        assertEquals(0, stages.size());
        assertEquals(4, errors.size());
        assertTrue(errors.get(0).contains("invalid type"), () -> String.valueOf(errors));
        assertTrue(errors.get(0).contains("'amount'"), () -> String.valueOf(errors));
        assertTrue(errors.get(1).contains("'execute'"), () -> String.valueOf(errors));
        assertTrue(errors.get(2).contains("'id'"), () -> String.valueOf(errors));
        assertTrue(errors.get(3).contains("'timeout'"), () -> String.valueOf(errors));
    }

    @Test
    @DisplayName("重复 id 注册失败并输出含 id 的错误")
    void duplicateIdRegistrationFailsExplicitly() throws IOException {
        loadScripts(Map.of(
                "first.js", "actions.register({id: 'sc_stage_dup', execute: function () { return 1; }});",
                "second.js", "actions.register({id: 'sc_stage_dup', execute: function () { return 1; }});"));
        assertEquals(1, stages.size());
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().contains("sc_stage_dup"), () -> String.valueOf(errors));
        assertTrue(errors.getFirst().contains("duplicate_id"), () -> String.valueOf(errors));
    }
}
