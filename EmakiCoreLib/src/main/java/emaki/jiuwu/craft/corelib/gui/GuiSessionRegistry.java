package emaki.jiuwu.craft.corelib.gui;

import java.util.UUID;

public interface GuiSessionRegistry {

    GuiSession activeSession(UUID viewerId);

    void removeSession(UUID viewerId, GuiSession session);
}
