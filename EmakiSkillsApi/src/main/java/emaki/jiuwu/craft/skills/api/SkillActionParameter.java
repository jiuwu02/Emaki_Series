package emaki.jiuwu.craft.skills.api;

/** Declared argument metadata for a skill-script action. */
public record SkillActionParameter(String name,
        SkillActionParameterType type,
        boolean required,
        String defaultValue,
        String description) {

    public SkillActionParameter {
        type = type == null ? SkillActionParameterType.STRING : type;
        defaultValue = defaultValue == null ? "" : defaultValue;
        description = description == null ? "" : description;
    }

    public static SkillActionParameter required(String name, SkillActionParameterType type, String description) {
        return new SkillActionParameter(name, type, true, "", description);
    }

    public static SkillActionParameter optional(String name, SkillActionParameterType type, String defaultValue, String description) {
        return new SkillActionParameter(name, type, false, defaultValue, description);
    }
}
