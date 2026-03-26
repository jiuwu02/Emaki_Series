package emaki.jiuwu.craft.corelib.operation;

public record OperationParameter(String name,
                                 OperationParameterType type,
                                 boolean required,
                                 String defaultValue,
                                 String description) {

    public OperationParameter {
        type = type == null ? OperationParameterType.STRING : type;
        defaultValue = defaultValue == null ? "" : defaultValue;
        description = description == null ? "" : description;
    }

    public static OperationParameter required(String name, OperationParameterType type, String description) {
        return new OperationParameter(name, type, true, "", description);
    }

    public static OperationParameter optional(String name, OperationParameterType type, String defaultValue, String description) {
        return new OperationParameter(name, type, false, defaultValue, description);
    }
}
