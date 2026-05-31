package emaki.jiuwu.craft.skills.api;

/**
 * Public service handle for the EmakiSkills skill-scripting system.
 *
 * <p>Registered with the Bukkit {@code ServicesManager} by EmakiSkills; obtain
 * it through {@link EmakiSkillsApiProvider#get()}. Its primary purpose is to
 * expose the {@link SkillScriptActionRegistry} so third-party plugins can
 * contribute custom skill-script actions.
 */
public interface EmakiSkillsApi {

    /** {@return the registry used to register custom skill-script actions} */
    SkillScriptActionRegistry scriptActionRegistry();
}
