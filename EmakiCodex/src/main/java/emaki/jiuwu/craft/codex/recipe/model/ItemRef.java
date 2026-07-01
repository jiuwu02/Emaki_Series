package emaki.jiuwu.craft.codex.recipe.model;

/**
 * A single ingredient or result reference inside a {@link CodexRecipe}.
 *
 * <p>The {@code shorthand} is the corelib ItemSource shorthand string
 * (e.g. {@code minecraft-diamond}, {@code craftengine-magic_sword}) which is the
 * stable, client-identifiable form used both for display and manual declaration.
 * When corelib cannot identify a stack's source the shorthand falls back to the
 * plain material name and {@code custom} is {@code false}.
 *
 * @param shorthand the corelib ItemSource shorthand
 * @param amount    the stack amount
 * @param custom    whether the item came from a non-vanilla source
 */
public record ItemRef(String shorthand, int amount, boolean custom) {

    public ItemRef {
        shorthand = shorthand == null ? "" : shorthand;
        amount = Math.max(1, amount);
    }

    /** {@return an empty/air reference placeholder} */
    public static ItemRef empty() {
        return new ItemRef("minecraft-air", 1, false);
    }

    /** {@return whether this reference is blank or air} */
    public boolean isEmpty() {
        return shorthand.isBlank() || shorthand.equals("minecraft-air");
    }
}
