package emaki.jiuwu.craft.corelib.gui;

import java.util.UUID;

/**
 * Lookup/removal bridge a {@link GuiBackend} uses to reach the {@link GuiService}
 * that owns a viewer's session.
 *
 * <p>The packet backend is a CoreLib-wide singleton shared by every plugin's
 * {@link GuiService}. When a client click/close packet arrives it only knows the
 * player, so it resolves the active session through the registry carried by the
 * session it last opened. Each {@link GuiService} registers itself as the
 * registry for the sessions it creates.</p>
 */
public interface GuiSessionRegistry {

    /**
     * The active session for the given viewer, or null if none is managed here.
     */
    GuiSession activeSession(UUID viewerId);

    /**
     * Removes the session for the viewer when it is the given session,
     * mirroring {@code ConcurrentHashMap#remove(key, value)} semantics.
     */
    void removeSession(UUID viewerId, GuiSession session);
}
