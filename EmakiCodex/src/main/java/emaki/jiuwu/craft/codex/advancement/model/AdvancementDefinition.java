package emaki.jiuwu.craft.codex.advancement.model;

import java.util.List;

/**
 * A single advancement node parsed from an advancement page file.
 *
 * <p>Text fields hold raw MiniMessage strings that are converted to JSON
 * components at registration time.
 *
 * @param id           the local advancement id (unique within its page)
 * @param icon         the item source shorthand for the icon (e.g. {@code minecraft-diamond})
 * @param title        MiniMessage title text
 * @param description  MiniMessage description text
 * @param frame        the frame style
 * @param x            the display grid x coordinate; only honored by the PacketEvents
 *                     coordinate channel (a value of {@code 0} means "not set")
 * @param y            the display grid y coordinate; only honored by the PacketEvents
 *                     coordinate channel (a value of {@code 0} means "not set")
 * @param parent       the parent advancement local id, or {@code null}/blank for the page root
 * @param hidden       whether the node stays hidden until completed
 * @param showToast    whether a toast pops on completion
 * @param announce     whether completion is broadcast to chat
 * @param completeActions corelib action lines from actions.complete executed when the advancement completes
 * @param triggers        automatic grant triggers that award this node when a matching
 *                     gameplay event fires and its condition passes
 */
public record AdvancementDefinition(String id,
        String icon,
        String title,
        String description,
        AdvancementFrame frame,
        double x,
        double y,
        String parent,
        boolean hidden,
        boolean showToast,
        boolean announce,
        List<String> completeActions,
        List<AdvancementTrigger> triggers) {

    /** The single manual criterion name used by every EmakiCodex advancement. */
    public static final String CRITERION = "codex";

    public AdvancementDefinition {
        completeActions = completeActions == null ? List.of() : List.copyOf(completeActions);
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
    }

    /** {@return whether this node is the root of its page (no parent)} */
    public boolean isRoot() {
        return parent == null || parent.isBlank();
    }

    /**
     * Whether this node carries an explicit display position. Vanilla advancement JSON
     * cannot express coordinates, so {@link #x} / {@link #y} only take effect through the
     * PacketEvents coordinate channel; a node left at the {@code 0}/{@code 0} default is
     * treated as "let the client auto-lay-out this node".
     *
     * @return {@code true} when either coordinate is non-zero
     */
    public boolean hasExplicitPosition() {
        return x != 0.0D || y != 0.0D;
    }
}
