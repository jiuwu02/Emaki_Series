package emaki.jiuwu.craft.cooking.model;

import java.util.function.Consumer;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public record StationInteraction(Player player,
        Block block,
        boolean leftClick,
        boolean rightClick,
        boolean mainHand,
        Consumer<Boolean> cancelConsumer) {

    public StationInteractionType type() {
        if (leftClick) {
            return isSneaking() ? StationInteractionType.SHIFT_LEFT_CLICK : StationInteractionType.LEFT_CLICK;
        }
        if (rightClick) {
            return isSneaking() ? StationInteractionType.SHIFT_RIGHT_CLICK : StationInteractionType.RIGHT_CLICK;
        }
        return null;
    }

    public boolean matches(StationInteractionType expected) {
        return expected != null && expected == type();
    }

    public boolean isSneaking() {
        return player != null && player.isSneaking();
    }

    public void cancel() {
        cancel(true);
    }

    public void cancel(boolean value) {
        if (cancelConsumer != null) {
            cancelConsumer.accept(value);
        }
    }
}
