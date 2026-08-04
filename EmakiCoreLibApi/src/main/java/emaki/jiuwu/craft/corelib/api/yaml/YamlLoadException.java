package emaki.jiuwu.craft.corelib.api.yaml;

public final class YamlLoadException extends RuntimeException {

    public YamlLoadException(String message) {
        super(message);
    }

    public YamlLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
