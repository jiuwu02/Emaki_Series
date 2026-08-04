package emaki.jiuwu.craft.codex.advancement.packet;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.advancements.Advancement;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementDisplay;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementHolder;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementProgress;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAdvancements;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;


public final class AchievementToastPacketBridge {

    private static final String CRITERION = "toast";

    private AchievementToastPacketBridge() {
    }

    public static boolean send(Player player, String key, String title, String description, ItemStack icon, String frame) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        ResourceLocation identifier = new ResourceLocation(key);
        AdvancementDisplay display = new AdvancementDisplay(
                MiniMessages.parse(title),
                MiniMessages.parse(description),
                packetIcon(icon),
                frameType(frame),
                null,
                true,
                false,
                0F,
                0F
        );
        Advancement advancement = new Advancement(null, display, List.of(List.of(CRITERION)), false);
        AdvancementHolder holder = new AdvancementHolder(identifier, advancement);
        AdvancementProgress progress = new AdvancementProgress(Map.of(
                CRITERION,
                new AdvancementProgress.CriterionProgress(System.currentTimeMillis())
        ));
        WrapperPlayServerUpdateAdvancements packet = new WrapperPlayServerUpdateAdvancements(
                false,
                List.of(holder),
                Collections.emptySet(),
                Map.of(identifier, progress),
                true
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        return true;
    }

    public static boolean remove(Player player, String key, String title, String description, ItemStack icon, String frame) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        ResourceLocation identifier = new ResourceLocation(key);
        WrapperPlayServerUpdateAdvancements packet = new WrapperPlayServerUpdateAdvancements(
                false,
                Collections.emptyList(),
                Set.of(identifier),
                Collections.emptyMap(),
                true
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        return true;
    }

    private static com.github.retrooper.packetevents.protocol.item.ItemStack packetIcon(ItemStack icon) {
        ItemStack safeIcon = icon == null || icon.getType().isAir() ? new ItemStack(Material.BOOK) : icon;
        return SpigotConversionUtil.fromBukkitItemStack(safeIcon);
    }

    private static AdvancementType frameType(String frame) {
        return switch (Texts.lower(frame)) {
            case "goal" -> AdvancementType.GOAL;
            case "challenge" -> AdvancementType.CHALLENGE;
            default -> AdvancementType.TASK;
        };
    }
}
