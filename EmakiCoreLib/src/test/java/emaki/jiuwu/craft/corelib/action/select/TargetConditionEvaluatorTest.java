package emaki.jiuwu.craft.corelib.action.select;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetCondition;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetConditionArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetOutcome;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;

@DisplayName("目标选择条件求值")
class TargetConditionEvaluatorTest {

    private final TargetConditionRegistry registry = new TargetConditionRegistry();
    private final TargetConditionEvaluator evaluator = new TargetConditionEvaluator(registry);
    private final List<String> reports = new ArrayList<>();

    @Test
    @DisplayName("空条件组恒为通过")
    void emptyGroupMatchesEverything() {
        assertTrue(matches(ConditionGroup.empty(), TargetFactsFixture.mob().build()));
    }

    @Test
    @DisplayName("entity_type 命中与未命中")
    void entityTypeMatches() {
        TargetFacts zombie = TargetFactsFixture.mob().type("ZOMBIE").build();
        assertTrue(matches(group("all_of", 0, typed("entity_type", Map.of("value", "zombie"))), zombie));
        assertFalse(matches(group("all_of", 0, typed("entity_type", Map.of("value", "SKELETON"))), zombie));
    }

    @Test
    @DisplayName("entity_type 列表按任一命中判定")
    void entityTypeListMatchesAny() {
        TargetFacts zombie = TargetFactsFixture.mob().type("ZOMBIE").build();
        assertTrue(matches(group("all_of", 0,
                typed("entity_type", Map.of("value", List.of("SKELETON", "ZOMBIE")))), zombie));
    }

    @Test
    @DisplayName("health_percent 按血量比例判定")
    void healthPercentCompares() {
        TargetFacts half = TargetFactsFixture.mob().health(10D, 20D).build();
        assertTrue(matches(group("all_of", 0,
                typed("health_percent", Map.of("op", "<=", "value", 50))), half));
        assertFalse(matches(group("all_of", 0,
                typed("health_percent", Map.of("op", ">", "value", 50))), half));
    }

    @Test
    @DisplayName("数值条件缺 op 或 value 时不可判定，默认排除目标")
    void numericConditionWithoutOperatorIsUnanswered() {
        TargetFacts half = TargetFactsFixture.mob().health(10D, 20D).build();
        assertFalse(matches(group("all_of", 0, typed("health", Map.of("value", 5))), half));
        assertFalse(matches(group("all_of", 0, typed("health", Map.of("op", ">"))), half));
    }

    @Test
    @DisplayName("玩家专属条件对非玩家不可判定")
    void playerOnlyConditionsAreUnansweredForMobs() {
        TargetFacts zombie = TargetFactsFixture.mob().build();
        assertFalse(matches(group("all_of", 0, typed("level", Map.of("op", ">=", "value", 1))), zombie));
        assertFalse(matches(group("all_of", 0, typed("food", Map.of("op", ">=", "value", 1))), zombie));
        assertFalse(matches(group("all_of", 0, typed("gamemode", Map.of("value", "survival"))), zombie));
    }

    @Test
    @DisplayName("玩家等级、饥饿与游戏模式按字面值判定")
    void playerValuesMatch() {
        TargetFacts steve = TargetFactsFixture.player().level(30).food(12).gameMode("creative").build();
        assertTrue(matches(group("all_of", 0, typed("level", Map.of("op", ">=", "value", 30))), steve));
        assertTrue(matches(group("all_of", 0, typed("food", Map.of("op", "<=", "value", 15))), steve));
        assertTrue(matches(group("all_of", 0, typed("gamemode", Map.of("value", "CREATIVE"))), steve));
        assertFalse(matches(group("all_of", 0, typed("gamemode", Map.of("value", "survival"))), steve));
    }

    @Test
    @DisplayName("permission 列表按任一命中判定")
    void permissionListMatchesAny() {
        TargetFacts steve = TargetFactsFixture.player().permission("emaki.admin", true).build();
        assertTrue(matches(group("all_of", 0,
                typed("permission", Map.of("value", List.of("emaki.other", "emaki.admin")))), steve));
        assertFalse(matches(group("all_of", 0,
                typed("permission", Map.of("value", "emaki.other"))), steve));
    }

    @Test
    @DisplayName("权限无法判定时条件不可判定，默认排除目标")
    void permissionWithoutProbeIsUnanswered() {
        TargetFacts zombie = TargetFactsFixture.mob().build();
        assertFalse(matches(group("all_of", 0, typed("permission", Map.of("value", "emaki.admin"))), zombie));
    }

    @Test
    @DisplayName("potion_effect 按效果与最小等级判定")
    void potionEffectMatches() {
        TargetFacts boosted = TargetFactsFixture.mob().effect("minecraft:speed", 1).build();
        assertTrue(matches(group("all_of", 0,
                typed("potion_effect", Map.of("effect", "speed"))), boosted));
        assertTrue(matches(group("all_of", 0,
                typed("potion_effect", Map.of("effect", "minecraft:speed", "min_amplifier", 1))), boosted));
        assertFalse(matches(group("all_of", 0,
                typed("potion_effect", Map.of("effect", "speed", "min_amplifier", 2))), boosted));
    }

    @Test
    @DisplayName("state 支持布尔期望值，未写期望时按 true 判定")
    void stateComparesBoolean() {
        TargetFacts walking = TargetFactsFixture.player().state("sneaking", false).build();
        assertFalse(matches(group("all_of", 0, typed("state", Map.of("key", "sneaking"))), walking));
        assertTrue(matches(group("all_of", 0,
                typed("state", Map.of("key", "sneaking", "value", false))), walking));
        assertFalse(matches(group("all_of", 0,
                typed("state", Map.of("key", "sneaking", "value", true))), walking));
    }

    @Test
    @DisplayName("状态名未知或该状态对主体不适用时不可判定")
    void unknownStateIsUnanswered() {
        TargetFacts walking = TargetFactsFixture.player().state("sneaking", false).build();
        assertFalse(matches(group("all_of", 0, typed("state", Map.of("key", "dancing"))), walking));
        assertFalse(matches(group("all_of", 0, typed("state", Map.of("key", "flying"))), walking));
    }

    @Test
    @DisplayName("distance 按与管线原点的距离判定")
    void distanceCompares() {
        TargetFacts far = TargetFactsFixture.mob().distance(12.5D).build();
        assertTrue(matches(group("all_of", 0, typed("distance", Map.of("op", "<=", "value", 16))), far));
        assertFalse(matches(group("all_of", 0, typed("distance", Map.of("op", ">=", "value", 16))), far));
    }

    @Test
    @DisplayName("world 支持命名空间写法与大小写差异")
    void worldMatchesNamespaced() {
        TargetFacts inWorld = TargetFactsFixture.mob().world("world_nether").build();
        assertTrue(matches(group("all_of", 0,
                typed("world", Map.of("value", "minecraft:world_nether"))), inWorld));
        assertFalse(matches(group("all_of", 0, typed("world", Map.of("value", "world"))), inWorld));
    }

    @Test
    @DisplayName("scoreboard_tag 按标签命中判定")
    void scoreboardTagMatches() {
        TargetFacts tagged = TargetFactsFixture.mob().tags("elite", "boss").build();
        assertTrue(matches(group("all_of", 0, typed("scoreboard_tag", Map.of("value", "boss"))), tagged));
        assertFalse(matches(group("all_of", 0, typed("scoreboard_tag", Map.of("value", "trash"))), tagged));
    }

    @Test
    @DisplayName("all_of / any_of / none_of 与 and / or / not 别名等价")
    void logicModesAndAliases() {
        TargetFacts zombie = TargetFactsFixture.mob().type("ZOMBIE").health(8D, 20D).build();
        Object type = typed("entity_type", Map.of("value", "ZOMBIE"));
        Object strong = typed("health", Map.of("op", ">=", "value", 15));

        assertFalse(matches(group("all_of", 0, type, strong), zombie));
        assertTrue(matches(group("any_of", 0, type, strong), zombie));
        assertTrue(matches(group("none_of", 0, strong), zombie));
        assertTrue(matches(group("not", 0, strong), zombie));

        assertFalse(matches(group("and", 0, type, strong), zombie));
        assertTrue(matches(group("or", 0, type, strong), zombie));
    }

    @Test
    @DisplayName("at_least 与 exactly 按命中数量判定")
    void requiredCountModes() {
        TargetFacts zombie = TargetFactsFixture.mob().type("ZOMBIE").health(8D, 20D).build();
        Object type = typed("entity_type", Map.of("value", "ZOMBIE"));
        Object weak = typed("health", Map.of("op", "<=", "value", 10));
        Object strong = typed("health", Map.of("op", ">=", "value", 15));

        assertTrue(matches(group("at_least", 2, type, weak, strong), zombie));
        assertFalse(matches(group("at_least", 3, type, weak, strong), zombie));
        assertTrue(matches(group("exactly", 2, type, weak, strong), zombie));
        assertFalse(matches(group("exactly", 3, type, weak, strong), zombie));
    }

    @Test
    @DisplayName("嵌套组按内层组合结果参与外层逻辑")
    void nestedGroupsCombine() {
        TargetFacts zombie = TargetFactsFixture.mob().type("ZOMBIE").build();
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("type", "any_of");
        inner.put("entries", List.of(
                typed("entity_type", Map.of("value", "SKELETON")),
                typed("entity_type", Map.of("value", "ZOMBIE"))));

        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("type", "all_of");
        outer.put("entries", List.of(inner, typed("world", Map.of("value", "world"))));

        assertTrue(matches(ConditionGroup.fromConfig(outer), zombie));
    }

    @Test
    @DisplayName("表达式节点可直接使用目标占位符")
    void expressionNodeUsesTargetPlaceholders() {
        TargetFacts half = TargetFactsFixture.mob().health(10D, 20D).build();
        assertTrue(matches(group("all_of", 0, "%target_health_percent% <= 50"), half));
        assertFalse(matches(group("all_of", 0, "%target_health_percent% > 50"), half));
    }

    @Test
    @DisplayName("表达式节点先展开目标占位符，再交给管线渲染器")
    void expressionNodeRendersPipelinePlaceholders() {
        TargetFacts zombie = TargetFactsFixture.mob().type("ZOMBIE").build();
        ConditionGroup group = group("all_of", 0, "%var.expected% == \"%target_type%\"");
        assertTrue(evaluator.matches(group, true, new TargetConditionContext(
                zombie, text -> text.replace("%var.expected%", "ZOMBIE"), null)));
        assertFalse(evaluator.matches(group, true, new TargetConditionContext(
                zombie, text -> text.replace("%var.expected%", "SKELETON"), null)));
    }

    @Test
    @DisplayName("未知类型默认排除目标，并只上报一次")
    void unknownTypeIsReportedOnce() {
        TargetConditionEvaluator reporting = new TargetConditionEvaluator(registry, reports::add);
        TargetFacts zombie = TargetFactsFixture.mob().build();
        ConditionGroup group = group("all_of", 0, typed("nonexistent_type", Map.of("value", 1)));

        assertFalse(reporting.matches(group, true, TargetConditionContext.of(zombie)));
        assertFalse(reporting.matches(group, true, TargetConditionContext.of(zombie)));
        assertTrue(reports.size() == 1, "同类型未知条件只上报一次");
    }

    @Test
    @DisplayName("invalid_as_failure 关闭时不可判定节点被跳过")
    void invalidAsFailureFalseSkipsUnansweredNodes() {
        TargetFacts zombie = TargetFactsFixture.mob().type("ZOMBIE").build();
        ConditionGroup group = group("all_of", 0,
                typed("level", Map.of("op", ">=", "value", 5)),
                typed("entity_type", Map.of("value", "ZOMBIE")));

        assertFalse(evaluator.matches(group, true, TargetConditionContext.of(zombie)));
        assertTrue(evaluator.matches(group, false, TargetConditionContext.of(zombie)));
    }

    @Test
    @DisplayName("第三方注册条件经宿主回调参与求值")
    void registeredConditionIsInvokedThroughHost() {
        CoreTargetCondition condition = new CoreTargetCondition() {

            @Override
            public String id() {
                return "test_always";
            }

            @Override
            public CoreTargetOutcome test(CoreActionSubject subject,
                    CoreStageContext context,
                    CoreTargetConditionArguments arguments) {
                return arguments.string("value", "").equals("yes")
                        ? CoreTargetOutcome.PASS
                        : CoreTargetOutcome.FAIL;
            }
        };
        assertTrue(registry.register(null, condition).successful());

        TargetFacts zombie = TargetFactsFixture.mob().build();
        TargetConditionContext context = new TargetConditionContext(zombie, null,
                (registered, arguments) -> registered.test(CoreActionSubject.absent(), null, arguments));

        assertTrue(evaluator.matches(group("all_of", 0, typed("test_always", Map.of("value", "yes"))),
                true, context));
        assertFalse(evaluator.matches(group("all_of", 0, typed("test_always", Map.of("value", "no"))),
                true, context));
    }

    @Test
    @DisplayName("身份条件按 id 与等级判定，系统未注册时不可判定")
    void identityConditionMatches() {
        TargetFacts mythic = TargetFactsFixture.mob().identity("mythicmobs", "SkeletonKing", 5D).build();
        assertTrue(matches(group("all_of", 0,
                typed("mythicmobs", Map.of("id", "SkeletonKing"))), mythic));
        assertFalse(matches(group("all_of", 0,
                typed("mythicmobs", Map.of("id", "OtherMob"))), mythic));
        assertTrue(matches(group("all_of", 0,
                typed("mythicmobs", Map.of("op", ">=", "level", 5))), mythic));
        assertFalse(matches(group("all_of", 0,
                typed("mythicmobs", Map.of("op", ">", "level", 5))), mythic));
        assertFalse(matches(group("all_of", 0,
                typed("emakimobs", Map.of("id", "elite_zombie"))), mythic));
    }

    @Test
    @DisplayName("身份系统已注册但实体不属于该系统时判定为不满足")
    void knownSystemWithoutIdentityFails() {
        TargetFacts plain = TargetFactsFixture.mob().knownSystem("emakimobs").build();
        assertFalse(matches(group("all_of", 0,
                typed("emakimobs", Map.of("id", "elite_zombie"))), plain));
        assertTrue(matches(group("none_of", 0,
                typed("emakimobs", Map.of("id", "elite_zombie"))), plain));
    }

    private boolean matches(ConditionGroup group, TargetFacts facts) {
        TargetConditionEvaluator reporting = new TargetConditionEvaluator(registry, reports::add);
        return reporting.matches(group, true, TargetConditionContext.of(facts));
    }

    private static ConditionGroup group(String type, int requiredCount, Object... entries) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("type", type);
        if (requiredCount > 0) {
            raw.put("required_count", requiredCount);
        }
        raw.put("entries", List.of(entries));
        return ConditionGroup.fromConfig(raw);
    }

    private static Map<String, Object> typed(String type, Map<String, Object> fields) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        node.putAll(fields);
        return node;
    }
}