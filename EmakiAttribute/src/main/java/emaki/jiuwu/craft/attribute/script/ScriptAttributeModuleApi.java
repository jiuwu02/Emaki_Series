package emaki.jiuwu.craft.attribute.script;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.script.ScriptServerApi.ScriptEntityApi;
import emaki.jiuwu.craft.corelib.api.script.modules.ScriptServiceApiSupport;

public final class ScriptAttributeModuleApi {

    private static final String SERVICE = "emaki.jiuwu.craft.attribute.api.PdcAttributeApi";
    private static final String FACADE = "emaki.jiuwu.craft.attribute.service.AttributeServiceFacade";

    private final ActionContext context;

    public ScriptAttributeModuleApi(ActionContext context) {
        this.context = context;
    }

    @HostAccess.Export
    public boolean available() {
        return ScriptServiceApiSupport.available(SERVICE);
    }

    @HostAccess.Export
    public boolean registerSource(String sourceId) {
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.invokeBoolean(service, "registerSource", new Class<?>[] { String.class }, sourceId))
                .orElse(false);
    }

    @HostAccess.Export
    public void unregisterSource(String sourceId) {
        ScriptServiceApiSupport.service(SERVICE)
                .ifPresent(service -> ScriptServiceApiSupport.invoke(service, "unregisterSource", new Class<?>[] { String.class }, sourceId));
    }

    @HostAccess.Export
    public boolean isRegisteredSource(String sourceId) {
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.invokeBoolean(service, "isRegisteredSource", new Class<?>[] { String.class }, sourceId))
                .orElse(false);
    }

    @HostAccess.Export
    public java.util.List<String> registeredSources() {
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.toStringList(ScriptServiceApiSupport.invoke(service, "registeredSources", new Class<?>[0])))
                .orElseGet(java.util.List::of);
    }

    @HostAccess.Export
    public Map<String, Object> read(String itemKey, String sourceId) {
        ItemStack itemStack = ScriptServiceApiSupport.item(context, itemKey);
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.payloadToMap(ScriptServiceApiSupport.invoke(service, "read", new Class<?>[] { ItemStack.class, String.class }, itemStack, sourceId)))
                .orElse(null);
    }

    @HostAccess.Export
    public Map<String, Object> readAll(String itemKey) {
        ItemStack itemStack = ScriptServiceApiSupport.item(context, itemKey);
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.payloadsToMap(ScriptServiceApiSupport.invoke(service, "readAll", new Class<?>[] { ItemStack.class }, itemStack)))
                .orElseGet(Map::of);
    }

    @HostAccess.Export
    public boolean write(String itemKey, String sourceId, Map<String, ?> attributes, Map<String, ?> meta) {
        ItemStack itemStack = ScriptServiceApiSupport.item(context, itemKey);
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.invokeBoolean(service,
                        "write",
                        new Class<?>[] { ItemStack.class, String.class, Map.class, Map.class },
                        itemStack,
                        sourceId,
                        ScriptServiceApiSupport.doubleMap(attributes),
                        ScriptServiceApiSupport.stringMap(meta)))
                .orElse(false);
    }

    @HostAccess.Export
    public boolean clear(String itemKey, String sourceId) {
        ItemStack itemStack = ScriptServiceApiSupport.item(context, itemKey);
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.invokeBoolean(service, "clear", new Class<?>[] { ItemStack.class, String.class }, itemStack, sourceId))
                .orElse(false);
    }

    @HostAccess.Export
    public void clearAll(String itemKey) {
        ItemStack itemStack = ScriptServiceApiSupport.item(context, itemKey);
        ScriptServiceApiSupport.service(SERVICE)
                .ifPresent(service -> ScriptServiceApiSupport.invoke(service, "clearAll", new Class<?>[] { ItemStack.class }, itemStack));
    }

    @HostAccess.Export
    public boolean applyDamage(ScriptEntityApi attacker, ScriptEntityApi target, String damageTypeId, double baseDamage, Map<String, ?> damageContext) {
        LivingEntity attackerEntity = living(attacker);
        LivingEntity targetEntity = living(target);
        if (targetEntity == null) {
            return false;
        }
        return ScriptServiceApiSupport.service(FACADE)
                .map(service -> ScriptServiceApiSupport.invokeBoolean(service,
                        "applyDamage",
                        new Class<?>[] { LivingEntity.class, LivingEntity.class, String.class, double.class, Map.class },
                        attackerEntity,
                        targetEntity,
                        damageTypeId,
                        baseDamage,
                        damageContext == null ? Map.of() : damageContext))
                .orElse(false);
    }

    @HostAccess.Export
    public Map<String, Object> calculateDamage(ScriptEntityApi attacker, ScriptEntityApi target, String damageTypeId, double baseDamage, Map<String, ?> damageContext) {
        LivingEntity attackerEntity = living(attacker);
        LivingEntity targetEntity = living(target);
        if (targetEntity == null) {
            return Map.of();
        }
        return ScriptServiceApiSupport.service(FACADE)
                .map(service -> ScriptServiceApiSupport.damageResultToMap(ScriptServiceApiSupport.invoke(service,
                        "calculateDamage",
                        new Class<?>[] { LivingEntity.class, LivingEntity.class, String.class, double.class, Map.class },
                        attackerEntity,
                        targetEntity,
                        damageTypeId,
                        baseDamage,
                        damageContext == null ? Map.of() : damageContext)))
                .orElseGet(Map::of);
    }

    @HostAccess.Export
    public void setDamageTypeOverride(ScriptEntityApi entity, String damageTypeId) {
        LivingEntity livingEntity = living(entity);
        if (livingEntity == null) {
            return;
        }
        ScriptServiceApiSupport.service(FACADE)
                .ifPresent(service -> ScriptServiceApiSupport.invoke(service,
                        "setDamageTypeOverride",
                        new Class<?>[] { LivingEntity.class, String.class },
                        livingEntity,
                        damageTypeId));
    }

    private LivingEntity living(ScriptEntityApi entity) {
        return entity != null && entity.entity() instanceof LivingEntity livingEntity ? livingEntity : null;
    }
}
