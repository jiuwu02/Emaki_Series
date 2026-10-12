package emaki.jiuwu.craft.structure.integration.furniture;

import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEvent;
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEventListener;
import emaki.jiuwu.craft.structure.multiblock.MultiblockService;

/**
 * Bridges CoreLib's normalised furniture events into multiblock detection. Replaces the four
 * per-source listeners that EmakiStructure used to register itself.
 *
 * <p>Interactions from the off-hand are ignored (what the source listeners did individually),
 * placement refreshes candidate anchors, and both break and hit re-check the chunk.
 */
public final class StructureFurnitureEventSubscriber implements FurnitureEventListener {

    private final MultiblockService service;

    public StructureFurnitureEventSubscriber(MultiblockService service) {
        this.service = service;
    }

    @Override
    public void onFurnitureEvent(FurnitureEvent event) {
        switch (event.type()) {
            case INTERACT -> {
                if (event.player() == null || !event.mainHand()) {
                    return;
                }
                service.onFurnitureInteract(event.player(), event.anchor(), event.rawId());
            }
            case PLACE -> service.onBlockPlaced(
                    event.anchor().getWorld(),
                    event.anchor().getX(),
                    event.anchor().getY(),
                    event.anchor().getZ(),
                    event.placerId());
            case BREAK, HIT -> service.onMemberChanged(
                    event.anchor().getWorld(),
                    event.anchor().getX(),
                    event.anchor().getY(),
                    event.anchor().getZ());
        }
    }
}
