package emaki.jiuwu.craft.corelib.action;

public record ActionParameter(String name,
        ActionParameterType type,
        boolean required,
        String defaultValue,
        String description) {

    public ActionParameter     {
        type = type == null ? ActionParameterType.STRING : type;
        defaultValue = defaultValue == null ? "" : defaultValue;
        description = description == null ? "" : description;
    }

    public static ActionParameter required(String name, ActionParameterType type, String description) {
        return new ActionParameter(name, type, true, "", description);
    }

    public static ActionParameter optional(String name, ActionParameterType type, String defaultValue, String description) {
        return new ActionParameter(name, type, false, defaultValue, description);
    }
}
