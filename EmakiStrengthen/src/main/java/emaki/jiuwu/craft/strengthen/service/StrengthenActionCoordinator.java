package emaki.jiuwu.craft.strengthen.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenProfile;
import net.kyori.adventure.text.Component;

public final class StrengthenActionCoordinator {

    private final EmakiStrengthenPlugin plugin;

    public StrengthenActionCoordinator(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public void triggerMilestoneActions(Player player,
            StrengthenProfile profile,
            java.util.Set<Integer> milestoneStars,
            ItemStack resultItem,
            int star) {
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (coreLib == null || coreLib.actionExecutor() == null || profile == null || milestoneStars == null || milestoneStars.isEmpty()) {
            return;
        }
        String showItem = buildShowItem(resultItem);
        for (Integer milestoneStar : milestoneStars) {
            StrengthenProfile.Milestone milestone = profile.milestone(milestoneStar);
            if (milestone == null || milestone.action().isEmpty()) {
                continue;
            }
            Map<String, String> placeholders = new LinkedHashMap<>();
            placeholders.put("strengthen_profile_id", profile.id());
            placeholders.put("strengthen_star", Integer.toString(star));
            placeholders.put("strengthen_show_item", showItem);
            placeholders.put("show_item", showItem);
            ActionContext context = new ActionContext(plugin, player, "strengthen_milestone", false, placeholders, Map.of("milestone", milestoneStar));
            coreLib.actionExecutor().executeAll(context, milestone.action(), true);
        }
    }

    public String buildShowItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "物品";
        }
        Component display = itemStack.hasItemMeta() && itemStack.getItemMeta().hasCustomName()
                ? itemStack.getItemMeta().customName()
                : itemStack.effectiveName();
        try {
            return MiniMessages.serialize(display.hoverEvent(itemStack.asHoverEvent(showItem -> showItem)));
        } catch (Exception ignored) {
            return MiniMessages.plain(display);
        }
    }
}
