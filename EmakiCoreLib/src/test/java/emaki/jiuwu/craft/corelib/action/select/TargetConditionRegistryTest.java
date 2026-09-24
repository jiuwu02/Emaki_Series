package emaki.jiuwu.craft.corelib.action.select;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetCondition;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetConditionArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentity;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentityProvider;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRegistration;

@DisplayName("目标条件与目标身份注册表")
class TargetConditionRegistryTest {

    @Test
    @DisplayName("空 id 与内置 id 注册被拒绝")
    void rejectsBlankAndBuiltinIds() {
        TargetConditionRegistry registry = new TargetConditionRegistry();

        CoreTargetRegistration blank = registry.register(null, condition(""));
        assertFalse(blank.successful());
        assertEquals("action.register.blank_id", blank.reasonKey());

        CoreTargetRegistration builtin = registry.register(null, condition("health_percent"));
        assertFalse(builtin.successful());
        assertEquals("action.register.condition_reserved", builtin.reasonKey());
        assertNull(registry.find("health_percent"));
    }

    @Test
    @DisplayName("重复 id 注册被拒绝，关闭后可重新注册")
    void duplicateThenReRegister() {
        TargetConditionRegistry registry = new TargetConditionRegistry();
        CoreTargetRegistration first = registry.register(null, condition("test_custom"));
        assertTrue(first.successful());

        CoreTargetRegistration duplicate = registry.register(null, condition("test_custom"));
        assertFalse(duplicate.successful());
        assertTrue(duplicate.reasonKey().startsWith("action.register.duplicate_id"));

        first.close();
        assertFalse(first.active());
        assertNull(registry.find("test_custom"));
        assertTrue(registry.register(null, condition("test_custom")).successful());
    }

    @Test
    @DisplayName("按 id 查找不区分大小写")
    void lookupIsCaseInsensitive() {
        TargetConditionRegistry registry = new TargetConditionRegistry();
        registry.register(null, condition("Test_Custom"));
        assertNotNull(registry.find("test_custom"));
        assertNotNull(registry.find("TEST_CUSTOM"));
    }

    @Test
    @DisplayName("按 owner 批量撤销在 owner 为空时不生效")
    void revokeAllRequiresOwner() {
        TargetConditionRegistry registry = new TargetConditionRegistry();
        registry.register(null, condition("test_custom"));
        assertEquals(0, registry.revokeAll(null));
        assertNotNull(registry.find("test_custom"));
    }

    @Test
    @DisplayName("身份提供者命中时写入身份表")
    void identityProviderResults() {
        TargetIdentityRegistry registry = new TargetIdentityRegistry();
        registry.register(null, provider("emakimobs", "elite_zombie"));

        assertEquals(List.of("emakimobs"), List.copyOf(registry.systemIds()));
        Map<String, CoreTargetIdentity> identified = registry.identifyAll(fakeEntity());
        assertEquals("elite_zombie", identified.get("emakimobs").id());
    }

    @Test
    @DisplayName("无实体时不调用身份提供者")
    void identitySkipsWithoutEntity() {
        List<String> reports = new ArrayList<>();
        TargetIdentityRegistry registry = new TargetIdentityRegistry(reports::add);
        registry.register(null, provider("emakimobs", "elite_zombie"));

        assertTrue(registry.identifyAll(null).isEmpty());
        assertEquals(0, reports.size());
    }

    @Test
    @DisplayName("身份提供者抛错时跳过该来源并只上报一次")
    void identityProviderFailureIsReportedOnce() {
        List<String> reports = new ArrayList<>();
        TargetIdentityRegistry registry = new TargetIdentityRegistry(reports::add);
        registry.register(null, new CoreTargetIdentityProvider() {

            @Override
            public String systemId() {
                return "broken";
            }

            @Override
            public CoreTargetIdentity identify(LivingEntity entity) {
                throw new IllegalStateException("boom");
            }
        });

        LivingEntity entity = fakeEntity();
        assertTrue(registry.identifyAll(entity).isEmpty());
        assertTrue(registry.identifyAll(entity).isEmpty());
        assertEquals(1, reports.size());
        assertTrue(reports.getFirst().contains("broken"));
    }

    @Test
    @DisplayName("身份系统 id 重复时后注册者失败，撤销后系统不再出现在身份表")
    void identityDuplicateAndRevoke() {
        TargetIdentityRegistry registry = new TargetIdentityRegistry();
        CoreTargetRegistration first = registry.register(null, provider("emakimobs", "elite_zombie"));
        assertTrue(first.successful());

        CoreTargetRegistration duplicate = registry.register(null, provider("emakimobs", "other"));
        assertFalse(duplicate.successful());
        assertTrue(duplicate.reasonKey().startsWith("action.register.duplicate_id"));

        first.close();
        assertTrue(registry.systemIds().isEmpty());
        assertTrue(registry.register(null, provider("emakimobs", "elite_zombie")).successful());
    }

    @Test
    @DisplayName("内置条件清单稳定且不包含生物系统 id")
    void builtinCatalogueIsStable() {
        assertTrue(BuiltinTargetConditions.contains("entity_type"));
        assertTrue(BuiltinTargetConditions.contains("ENTITY_TYPE"));
        assertFalse(BuiltinTargetConditions.contains("mythicmobs"));
        assertFalse(BuiltinTargetConditions.contains("unknown_type"));
        assertEquals(14, BuiltinTargetConditions.ids().size());
    }

    private static LivingEntity fakeEntity() {
        return (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[] { LivingEntity.class },
                (proxy, method, arguments) -> {
                    if ("toString".equals(method.getName())) {
                        return "fake-entity";
                    }
                    Class<?> type = method.getReturnType();
                    if (!type.isPrimitive()) {
                        return null;
                    }
                    if (type == boolean.class) {
                        return false;
                    }
                    if (type == double.class) {
                        return 0D;
                    }
                    if (type == float.class) {
                        return 0F;
                    }
                    if (type == long.class) {
                        return 0L;
                    }
                    if (type == char.class) {
                        return (char) 0;
                    }
                    if (type == byte.class) {
                        return (byte) 0;
                    }
                    if (type == short.class) {
                        return (short) 0;
                    }
                    return 0;
                });
    }

    private static CoreTargetCondition condition(String id) {
        return new CoreTargetCondition() {

            @Override
            public String id() {
                return id;
            }

            @Override
            public CoreTargetOutcome test(CoreActionSubject subject,
                    CoreStageContext context,
                    CoreTargetConditionArguments arguments) {
                return CoreTargetOutcome.PASS;
            }
        };
    }

    private static CoreTargetIdentityProvider provider(String systemId, String mobId) {
        return new CoreTargetIdentityProvider() {

            @Override
            public String systemId() {
                return systemId;
            }

            @Override
            public CoreTargetIdentity identify(LivingEntity entity) {
                return new CoreTargetIdentity(systemId, mobId, 0D);
            }
        };
    }
}