package emaki.jiuwu.craft.corelib.action.select;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("目标占位符展开")
class TargetPlaceholdersTest {

    @Test
    @DisplayName("生物事实按字面值展开")
    void expandsMobFacts() {
        TargetFacts facts = TargetFactsFixture.mob()
                .type("ZOMBIE")
                .health(8D, 20D)
                .distance(12.3456D)
                .world("world_nether")
                .build();

        assertEquals("ZOMBIE", TargetPlaceholders.expand("%target_type%", facts));
        assertEquals("8", TargetPlaceholders.expand("%target_health%", facts));
        assertEquals("20", TargetPlaceholders.expand("%target_max_health%", facts));
        assertEquals("40", TargetPlaceholders.expand("%target_health_percent%", facts));
        assertEquals("12.346", TargetPlaceholders.expand("%target_distance%", facts));
        assertEquals("world_nether", TargetPlaceholders.expand("%target_world%", facts));
    }

    @Test
    @DisplayName("玩家事实只在玩家目标上展开")
    void expandsPlayerFacts() {
        TargetFacts steve = TargetFactsFixture.player().level(42).food(7).gameMode("creative").build();
        assertEquals("42", TargetPlaceholders.expand("%target_level%", steve));
        assertEquals("7", TargetPlaceholders.expand("%target_food%", steve));
        assertEquals("creative", TargetPlaceholders.expand("%target_gamemode%", steve));

        TargetFacts zombie = TargetFactsFixture.mob().build();
        assertEquals("%target_level%", TargetPlaceholders.expand("%target_level%", zombie));
        assertEquals("%target_gamemode%", TargetPlaceholders.expand("%target_gamemode%", zombie));
    }

    @Test
    @DisplayName("身份占位符按系统 id 展开，未注册系统保持原样")
    void expandsIdentityPlaceholders() {
        TargetFacts facts = TargetFactsFixture.mob()
                .identity("mythicmobs", "SkeletonKing", 7D)
                .knownSystem("emakimobs")
                .build();

        assertEquals("SkeletonKing", TargetPlaceholders.expand("%target_mythicmobs_id%", facts));
        assertEquals("7", TargetPlaceholders.expand("%target_mythicmobs_level%", facts));
        assertEquals("%target_emakimobs_id%", TargetPlaceholders.expand("%target_emakimobs_id%", facts));
    }

    @Test
    @DisplayName("权限占位符按探针结果展开")
    void expandsPermissionPlaceholders() {
        TargetFacts steve = TargetFactsFixture.player().permission("emaki.admin", true).build();
        assertEquals("true", TargetPlaceholders.expand("%target_permission_emaki.admin%", steve));
        assertEquals("false", TargetPlaceholders.expand("%target_permission_emaki.other%", steve));

        TargetFacts zombie = TargetFactsFixture.mob().build();
        assertEquals("%target_permission_emaki.admin%",
                TargetPlaceholders.expand("%target_permission_emaki.admin%", zombie));
    }

    @Test
    @DisplayName("未知占位符与其他文本原样保留")
    void keepsUnknownTokens() {
        TargetFacts facts = TargetFactsFixture.mob().distance(-1D).build();
        assertEquals("%other%", TargetPlaceholders.expand("%other%", facts));
        assertEquals("%target_distance%", TargetPlaceholders.expand("%target_distance%", facts));
        assertEquals("HP 20 / 20", TargetPlaceholders.expand("HP %target_health% / 20", facts));
    }
}