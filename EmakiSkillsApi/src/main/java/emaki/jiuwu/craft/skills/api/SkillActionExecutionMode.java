package emaki.jiuwu.craft.skills.api;

/** Scheduling mode declared by a skill-script action. */
public enum SkillActionExecutionMode {
    /** Invoke the action on the script's owned Bukkit/Paper/Folia scheduler domain. */
    SYNC,
    /** Invoke the action on CoreLib's asynchronous task scheduler; Bukkit ownership rules still apply. */
    ASYNC_IO
}
