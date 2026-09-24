package emaki.jiuwu.craft.corelib.action.select;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.condition.ConditionGroup;

@DisplayName("行内谓词串解析与求值")
class TargetPredicateParserTest {

    private final TargetConditionEvaluator evaluator = new TargetConditionEvaluator(new TargetConditionRegistry());

    @Test
    @DisplayName("空格相邻的谓词按 AND 组合")
    void spaceSeparatedPredicatesAreAnd() {
        assertTrue(matches("entity_type=ZOMBIE health_percent<=50",
                TargetFactsFixture.mob().type("ZOMBIE").health(8D, 20D).build()));
        assertFalse(matches("entity_type=ZOMBIE health_percent<=50",
                TargetFactsFixture.mob().type("ZOMBIE").health(18D, 20D).build()));
        assertFalse(matches("entity_type=ZOMBIE health_percent<=50",
                TargetFactsFixture.mob().type("SKELETON").health(8D, 20D).build()));
    }

    @Test
    @DisplayName("双竖线按 OR 组合，叹号按 NOT，圆括号分组")
    void logicOperators() {
        TargetFacts steve = TargetFactsFixture.player().level(40).build();
        assertTrue(matches("level>=30 || permission=emaki.vip", steve));
        assertFalse(matches("level>=50 || permission=emaki.vip", steve));
        assertTrue(matches("!gamemode=creative", steve));
        assertFalse(matches("!(level>=30)", steve));
        assertTrue(matches("(level>=50 || level>=30) food>=1", steve));
        assertFalse(matches("(level>=50 || level>=45) food>=1", steve));
    }

    @Test
    @DisplayName("逗号分隔的值按任一命中判定，叹号取反")
    void valueLists() {
        TargetFacts zombie = TargetFactsFixture.mob().type("ZOMBIE").build();
        assertTrue(matches("entity_type=ZOMBIE,SKELETON", zombie));
        assertFalse(matches("entity_type=SKELETON,HUSK", zombie));
        assertTrue(matches("entity_type!=SKELETON,HUSK", zombie));
    }

    @Test
    @DisplayName("状态子维度支持布尔期望与取反")
    void statePredicates() {
        TargetFacts sneaking = TargetFactsFixture.player().state("sneaking", true).build();
        assertTrue(matches("state.sneaking", sneaking));
        assertTrue(matches("state.sneaking=true", sneaking));
        assertFalse(matches("state.sneaking=false", sneaking));
        assertFalse(matches("state.sneaking!=true", sneaking));
        assertTrue(matches("state.sneaking!=false", sneaking));
    }

    @Test
    @DisplayName("效果子维度按最低等级判定，不存在时判为不满足")
    void effectPredicates() {
        TargetFacts boosted = TargetFactsFixture.mob().effect("minecraft:speed", 2).build();
        assertTrue(matches("potion_effect.speed>=2", boosted));
        assertTrue(matches("potion_effect.minecraft:speed>=0", boosted));
        assertFalse(matches("potion_effect.speed>=3", boosted));
        assertFalse(matches("potion_effect.jump_boost>=0", boosted));
    }

    @Test
    @DisplayName("生物系统身份按 id 与等级判定")
    void identityPredicates() {
        TargetFacts mythic = TargetFactsFixture.mob().identity("mythicmobs", "SkeletonKing", 7D).build();
        assertTrue(matches("mythicmobs=SkeletonKing", mythic));
        assertTrue(matches("mythicmobs=SkeletonKing,ZombieLord", mythic));
        assertFalse(matches("mythicmobs=ZombieLord", mythic));
        assertTrue(matches("mythicmobs_level>=5", mythic));
        assertFalse(matches("mythicmobs_level>=8", mythic));

        TargetFacts noSystem = TargetFactsFixture.mob().build();
        assertFalse(matches("mythicmobs=SkeletonKing", noSystem));
    }

    @Test
    @DisplayName("系统未注册时身份条件不可判定，默认排除目标（含取反写法）")
    void unregisteredSystemIsUnanswered() {
        TargetFacts noSystem = TargetFactsFixture.mob().build();
        assertFalse(matches("!emakimobs=elite_zombie", noSystem));

        TargetFacts knownSystem = TargetFactsFixture.mob().knownSystem("emakimobs").build();
        assertTrue(matches("!emakimobs=elite_zombie", knownSystem));
        assertFalse(matches("emakimobs=elite_zombie", knownSystem));
    }

    @Test
    @DisplayName("坐标距离与权限谓词按事实判定")
    void distanceAndPermission() {
        TargetFacts close = TargetFactsFixture.mob().distance(6D).build();
        assertTrue(matches("distance<=8", close));
        assertFalse(matches("distance>8", close));

        TargetFacts steve = TargetFactsFixture.player().permission("emaki.admin", true).build();
        assertTrue(matches("permission=emaki.admin", steve));
        assertFalse(matches("permission=emaki.other", steve));
    }

    @Test
    @DisplayName("空谓词与无法解析的谓词返回带位置的诊断")
    void invalidPredicates() {
        TargetPredicateParser.Result blank = TargetPredicateParser.parse("  ");
        assertEquals("action.gate.filter.condition_required", ((TargetPredicateParser.Result.Invalid) blank).reasonKey());

        assertInvalid("health");
        assertInvalid("health~50");
        assertInvalid("level>=abc");
        assertInvalid("(level>=30");
        assertInvalid("helth>=50");
        assertInvalid("state.sneaking>=true");
        assertInvalid("potion_effect.speed>2");
    }

    @Test
    @DisplayName("错误诊断带上出错的 token 与可用键清单")
    void invalidPredicateCarriesDiagnostics() {
        TargetPredicateParser.Result.Invalid invalid =
                (TargetPredicateParser.Result.Invalid) TargetPredicateParser.parse("entity_type=ZOMBIE helth>=50");
        assertEquals("helth>=50", invalid.args().get("token"));
        assertTrue(invalid.args().get("keys").toString().contains("health_percent"));
    }

    private static void assertInvalid(String raw) {
        TargetPredicateParser.Result result = TargetPredicateParser.parse(raw);
        assertTrue(result instanceof TargetPredicateParser.Result.Invalid, "应判为无效谓词：" + raw);
    }

    private boolean matches(String predicates, TargetFacts facts) {
        TargetPredicateParser.Result result = TargetPredicateParser.parse(predicates);
        assertTrue(result instanceof TargetPredicateParser.Result.Parsed, "应能解析：" + predicates);
        ConditionGroup group = ((TargetPredicateParser.Result.Parsed) result).group();
        return evaluator.matches(group, true, TargetConditionContext.of(facts));
    }
}