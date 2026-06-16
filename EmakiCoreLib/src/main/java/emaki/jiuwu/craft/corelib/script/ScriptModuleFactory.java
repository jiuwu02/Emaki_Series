package emaki.jiuwu.craft.corelib.script;

@FunctionalInterface
public interface ScriptModuleFactory {

    Object create(ScriptModuleContext context);
}
