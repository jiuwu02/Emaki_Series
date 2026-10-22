package emaki.jiuwu.craft.corelib.api.yaml;

/**
 * Unchecked exception thrown when a YAML document cannot be read, parsed or written.
 *
 * <p>The loaders wrap the original failure with an operator-facing (Chinese) message that names
 * the operation that failed, and keep the underlying exception attached as the cause.
 */
public final class YamlLoadException extends RuntimeException {

    /**
     * Creates an exception with a message and no cause.
     *
     * @param message operator-facing description of the failure
     */
    public YamlLoadException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and the underlying cause.
     *
     * @param message operator-facing description of the failure
     * @param cause the failure that triggered this exception; may be {@code null}
     */
    public YamlLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
