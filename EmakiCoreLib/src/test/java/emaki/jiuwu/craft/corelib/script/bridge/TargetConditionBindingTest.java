package emaki.jiuwu.craft.corelib.script.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import emaki.jiuwu.craft.corelib.action.select.TargetConditionRegistry;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetCondition;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetConditionArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRegistration;
import emaki.jiuwu.craft.corelib.script.host.ScriptHost;
import emaki.jiuwu.craft.corelib.script.host.ScriptHostSettings;

@DisplayName("目标条件绑定桥")
class TargetConditionBindingTest {

    @TempDir
    Path dataDirectory;

    private ScriptHost host;
    private TargetConditionRegistry conditionRegistry;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warns = new ArrayList<>();
    private final List<CoreTargetRegistration> handles = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (host != null) {
            host.close();
            host = null;
        }
        for (CoreTargetRegistration handle : handles) {
            handle.close();
        }
        handles.clear();
        errors.clear();
        warns.clear();
    }

    private void loadScripts(Map<String, String> files) throws IOException {
        Path scripts = dataDirectory.resolve("scripts");
        Files.createDirectories(scripts);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Files.writeString(scripts.resolve(entry.getKey()), entry.getValue());
        }
        conditionRegistry = new TargetConditionRegistry();
        TargetConditionScriptBinding binding = new TargetConditionScriptBinding(
                () -> conditionRegistry,
                null,
                () -> host == null ? null : host.callbackRunner(),
                reason -> reason,
                errors::add,
                warns::add,
                handles);
        host = new ScriptHost(null, scripts, new ScriptHostSettings(true, 500, false),
                Map.of("target_conditions", binding));
        host.load();
    }

    private CoreTargetOutcome testCondition(String id, CoreTargetConditionArguments arguments) {
        CoreTargetCondition condition = conditionRegistry.find(id);
        assertNotNull(condition, () -> "condition not registered: " + id);
        return condition.test(CoreActionSubject.absent(), new StubStageContext(), arguments);
    }

    @Test
    @DisplayName("JS 谓词按真值转换为 PASS/FAIL")
    void jsPredicateConvertsTruthiness() throws IOException {
        loadScripts(Map.of(
                "truthy.js", "target_conditions.register({id: 'sc_cond_true', "
                        + "description: 'always true', test: function (ctx) { return 1; }});",
                "falsy.js", "target_conditions.register({id: 'sc_cond_false', "
                        + "test: function (ctx) { return 0; }});",
                "nullish.js", "target_conditions.register({id: 'sc_cond_nullish', "
                        + "test: function (ctx) { return null; }});"));
        assertTrue(errors.isEmpty(), () -> "unexpected errors: " + errors);
        assertEquals(3, handles.size());
        assertEquals(CoreTargetOutcome.PASS, testCondition("sc_cond_true", CoreTargetConditionArguments.empty()));
        assertEquals(CoreTargetOutcome.FAIL, testCondition("sc_cond_false", CoreTargetConditionArguments.empty()));
        assertEquals(CoreTargetOutcome.FAIL, testCondition("sc_cond_nullish", CoreTargetConditionArguments.empty()));
    }

    @Test
    @DisplayName("ctx 暴露目标、上下文与条件节点字段")
    void contextExposesTargetContextAndArguments() throws IOException {
        loadScripts(Map.of("fields.js", """
                target_conditions.register({
                    id: 'sc_cond_fields',
                    test: function (ctx) {
                        return ctx.target.getType() === 'absent'
                            && ctx.context.getPhase() === 'stub'
                            && ctx.arguments.expected === 'yes';
                    }
                });
                """));
        assertTrue(errors.isEmpty(), () -> "unexpected errors: " + errors);
        assertEquals(CoreTargetOutcome.PASS, testCondition("sc_cond_fields",
                CoreTargetConditionArguments.of(Map.of("expected", "yes"))));
        assertEquals(CoreTargetOutcome.FAIL, testCondition("sc_cond_fields",
                CoreTargetConditionArguments.of(Map.of("expected", "no"))));
    }

    @Test
    @DisplayName("JS 谓词抛异常按「不成立」处理并输出含 id 的告警")
    void throwingPredicateFailsWithWarning() throws IOException {
        loadScripts(Map.of("boom.js",
                "target_conditions.register({id: 'sc_cond_boom', "
                        + "test: function (ctx) { throw new Error('boom'); }});"));
        assertEquals(CoreTargetOutcome.FAIL,
                testCondition("sc_cond_boom", CoreTargetConditionArguments.empty()));
        assertEquals(1, warns.size());
        assertTrue(warns.getFirst().contains("sc_cond_boom"), () -> String.valueOf(warns));
    }

    @Test
    @DisplayName("重复 id 与内置保留 id 被显式拒绝")
    void duplicateAndReservedIdsAreRejected() throws IOException {
        loadScripts(Map.of(
                "first.js", "target_conditions.register({id: 'sc_cond_dup', test: function (ctx) { return true; }});",
                "reserved.js", "target_conditions.register({id: 'player', test: function (ctx) { return true; }});",
                "second.js", "target_conditions.register({id: 'sc_cond_dup', test: function (ctx) { return true; }});"));
        assertEquals(1, handles.size());
        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("condition_reserved"), () -> String.valueOf(errors));
        assertTrue(errors.get(1).contains("sc_cond_dup"), () -> String.valueOf(errors));
        assertTrue(errors.get(1).contains("duplicate"), () -> String.valueOf(errors));
    }

    @Test
    @DisplayName("缺失 id 或 test 被显式拒绝")
    void invalidPayloadsAreRejectedExplicitly() throws IOException {
        loadScripts(Map.of(
                "a_no_id.js", "target_conditions.register({test: function (ctx) { return true; }});",
                "b_no_test.js", "target_conditions.register({id: 'sc_cond_no_test'});"));
        assertEquals(0, handles.size());
        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("'id'"), () -> String.valueOf(errors));
        assertTrue(errors.get(1).contains("'test'"), () -> String.valueOf(errors));
    }

    private static final class StubStageContext implements CoreStageContext {

        @Override
        public org.bukkit.plugin.Plugin sourcePlugin() {
            return null;
        }

        @Override
        public @org.jetbrains.annotations.NotNull CoreActionSubject caster() {
            return CoreActionSubject.absent();
        }

        @Override
        public @org.jetbrains.annotations.NotNull List<CoreActionSubject> targets() {
            return List.of();
        }

        @Override
        public @org.jetbrains.annotations.NotNull CoreActionSubject currentTarget() {
            return CoreActionSubject.absent();
        }

        @Override
        public int currentTargetIndex() {
            return 0;
        }

        @Override
        public @org.jetbrains.annotations.NotNull Location origin() {
            return new Location(null, 0.0D, 0.0D, 0.0D);
        }

        @Override
        public @org.jetbrains.annotations.NotNull String phase() {
            return "stub";
        }

        @Override
        public boolean silent() {
            return false;
        }

        @Override
        public @org.jetbrains.annotations.NotNull <T> Optional<T> get(@org.jetbrains.annotations.NotNull CoreActionKey<T> key) {
            return Optional.empty();
        }

        @Override
        public @org.jetbrains.annotations.NotNull <T> T require(@org.jetbrains.annotations.NotNull CoreActionKey<T> key) {
            throw new IllegalStateException("stub context has no values");
        }

        @Override
        public @org.jetbrains.annotations.NotNull List<CoreActionKey<?>> presentKeys() {
            return List.of();
        }

        @Override
        public @org.jetbrains.annotations.NotNull Optional<String> variable(@org.jetbrains.annotations.Nullable String name) {
            return Optional.empty();
        }

        @Override
        public @org.jetbrains.annotations.NotNull String render(@org.jetbrains.annotations.Nullable String template) {
            return template == null ? "" : template;
        }
    }
}
