package emaki.jiuwu.craft.corelib.api.script.modules;

import org.graalvm.polyglot.HostAccess;

public final class ScriptSkillsModuleApi {

    private static final String SERVICE = "emaki.jiuwu.craft.skills.api.EmakiSkillsApi";

    @HostAccess.Export
    public boolean available() {
        return ScriptServiceApiSupport.available(SERVICE);
    }

    @HostAccess.Export
    public boolean hasScriptAction(String actionId) {
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> {
                    Object registry = ScriptServiceApiSupport.invoke(service, "scriptActionRegistry", new Class<?>[0]);
                    return ScriptServiceApiSupport.invoke(registry, "get", new Class<?>[] { String.class }, actionId) != null;
                })
                .orElse(false);
    }

    @HostAccess.Export
    public java.util.List<String> registeredScriptActions() {
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> {
                    Object registry = ScriptServiceApiSupport.invoke(service, "scriptActionRegistry", new Class<?>[0]);
                    Object all = ScriptServiceApiSupport.invoke(registry, "all", new Class<?>[0]);
                    return all instanceof java.util.Map<?, ?> map
                            ? ScriptServiceApiSupport.toStringList(map.keySet())
                            : java.util.List.<String>of();
                })
                .orElseGet(java.util.List::of);
    }
}
