package emaki.jiuwu.craft.corelib.script.js.registration;

/**
 * Built-in JavaScript registration type names owned by CoreLib itself.
 *
 * <p>The registration system identifies each entry by a free-form lowercase
 * string instead of a fixed enum, so feature plugins (Forge, Gem, Cooking, ...)
 * can register their own types without CoreLib having to know about them. This
 * class only declares the handful of types CoreLib provides natively; plugin
 * types are declared as local constants in each plugin's own registry.</p>
 */
public final class JavaScriptRegistrationTypes {

    public static final String ACTION = "action";
    public static final String PLACEHOLDER = "placeholder";
    public static final String EVENT = "event";
    public static final String CONDITION = "condition";
    public static final String EXPRESSION_FUNCTION = "expression_function";

    private JavaScriptRegistrationTypes() {
    }
}
