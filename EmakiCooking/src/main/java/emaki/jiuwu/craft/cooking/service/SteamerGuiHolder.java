package emaki.jiuwu.craft.cooking.service;

import java.util.UUID;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class SteamerGuiHolder implements InventoryHolder {

    private final UUID viewerId;
    private final StationCoordinates coordinates;
    private Inventory inventory;
    private boolean suppressSave;

    SteamerGuiHolder(UUID viewerId, StationCoordinates coordinates) {
        this.viewerId = viewerId;
        this.coordinates = coordinates;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    UUID viewerId() {
        return viewerId;
    }

    StationCoordinates coordinates() {
        return coordinates;
    }

    boolean suppressSave() {
        return suppressSave;
    }

    void setSuppressSave(boolean suppressSave) {
        this.suppressSave = suppressSave;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
