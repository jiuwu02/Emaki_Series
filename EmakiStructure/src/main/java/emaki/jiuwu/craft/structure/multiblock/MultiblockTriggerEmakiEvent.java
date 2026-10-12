package emaki.jiuwu.craft.structure.multiblock;

import emaki.jiuwu.craft.corelib.event.EmakiEvent;

import java.util.UUID;

public record MultiblockTriggerEmakiEvent(String multiblockId, UUID playerId, String playerName,
                                          String worldName, int x, int y, int z, String facing)
        implements EmakiEvent {

    public static final String TYPE = "structure.multiblock_trigger";

    @Override
    public String eventType() {
        return TYPE;
    }
}
