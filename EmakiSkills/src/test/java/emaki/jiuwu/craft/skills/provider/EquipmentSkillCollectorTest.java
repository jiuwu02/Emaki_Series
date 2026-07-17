package emaki.jiuwu.craft.skills.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import io.papermc.paper.persistence.PersistentDataContainerView;
import emaki.jiuwu.craft.skills.model.BoundSkillTrigger;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;

class EquipmentSkillCollectorTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final NamespacedKey SKILL_IDS_KEY = new NamespacedKey("emaki_skills", "item.skills.ids");
    private static final NamespacedKey ACTIVE_SLOT_KEY = new NamespacedKey("emaki_skills", "item.skills.active_slot");
    private static final NamespacedKey TRIGGER_BINDINGS_KEY = new NamespacedKey("emaki_skills", "item.skills.triggers");

    @Test
    void bindingsUseDirectPdcWhenItemMetaThrows() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        Player player = player(inventory);
        ItemStack item = item(Material.DIAMOND_SWORD, pdc(null, "main_hand", "fireball=right-click"));
        when(item.getItemMeta()).thenThrow(new NullPointerException("corrupt meta"));
        when(inventory.getItem(EquipmentSlot.HAND)).thenReturn(item);

        List<BoundSkillTrigger> bindings = collector(mock(Logger.class)).collectBoundTriggers(player, "right_click");

        assertEquals(List.of(new BoundSkillTrigger("fireball", "right_click", "main_hand")), bindings);
        verify(item).getPersistentDataContainer();
        verify(item, never()).getItemMeta();
        verify(item, never()).hasItemMeta();
    }

    @Test
    void pdcSkillsSurviveRepeatedLoreFailureWithoutDuplicateLogs() {
        Logger logger = mock(Logger.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Player player = player(inventory);
        ItemStack item = item(Material.NETHERITE_SWORD, pdc("alpha; beta", "main_hand", null));
        when(item.getItemMeta()).thenThrow(new NullPointerException("corrupt meta"));
        when(inventory.getItem(EquipmentSlot.HAND)).thenReturn(item);
        EquipmentSkillCollector collector = collector(logger);

        List<UnlockedSkillEntry> first = collector.collect(player);
        List<UnlockedSkillEntry> second = collector.collect(player);

        assertEquals(List.of("alpha", "beta"), first.stream().map(UnlockedSkillEntry::skillId).toList());
        assertEquals(List.of("alpha", "beta"), second.stream().map(UnlockedSkillEntry::skillId).toList());
        verify(logger, times(1)).warning("[EquipmentSkillCollector] Lore read failed: player=" + PLAYER_ID
                + ", slot=main_hand, material=NETHERITE_SWORD, exception=java.lang.NullPointerException");
    }

    @Test
    void brokenSlotDoesNotBlockOtherSlots() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        Player player = player(inventory);
        ItemStack broken = item(Material.DIAMOND_SWORD, pdc("broken_skill", "main_hand", null));
        ItemStack valid = item(Material.SHIELD, pdc("valid_skill", "off_hand", null));
        when(broken.getItemMeta()).thenThrow(new LinkageError("broken implementation"));
        when(valid.getItemMeta()).thenReturn(null);
        when(inventory.getItem(EquipmentSlot.HAND)).thenReturn(broken);
        when(inventory.getItem(EquipmentSlot.OFF_HAND)).thenReturn(valid);

        List<UnlockedSkillEntry> entries = collector(mock(Logger.class)).collect(player);

        assertEquals(List.of("broken_skill", "valid_skill"),
                entries.stream().map(UnlockedSkillEntry::skillId).toList());
        verify(valid).getPersistentDataContainer();
        verify(valid).getItemMeta();
    }

    @Test
    void ignoresNullAirAndInvalidBindings() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        Player player = player(inventory);
        ItemStack air = item(Material.AIR, pdc(null, null, "air_skill=right_click"));
        ItemStack invalid = item(Material.DIAMOND_HELMET,
                pdc(null, "helmet", "missing-separator;=right_click;skill=; ; = "));
        when(inventory.getItem(EquipmentSlot.OFF_HAND)).thenReturn(air);
        when(inventory.getItem(EquipmentSlot.HEAD)).thenReturn(invalid);

        List<BoundSkillTrigger> bindings = collector(mock(Logger.class)).collectBoundTriggers(player, "right-click");

        assertTrue(bindings.isEmpty());
        verify(air, never()).getPersistentDataContainer();
        verify(air, never()).getItemMeta();
        verify(air, never()).hasItemMeta();
        verify(invalid).getPersistentDataContainer();
        verify(invalid, never()).getItemMeta();
        verify(invalid, never()).hasItemMeta();
    }

    private static EquipmentSkillCollector collector(Logger logger) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(logger);
        return new EquipmentSkillCollector(plugin, Map::of);
    }

    private static Player player(PlayerInventory inventory) {
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        return player;
    }

    private static ItemStack item(Material material, PersistentDataContainerView pdc) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getPersistentDataContainer()).thenReturn(pdc);
        return item;
    }

    private static PersistentDataContainerView pdc(String skills, String activeSlot, String bindings) {
        PersistentDataContainerView pdc = mock(PersistentDataContainerView.class);
        when(pdc.get(SKILL_IDS_KEY, PersistentDataType.STRING)).thenReturn(skills);
        when(pdc.get(ACTIVE_SLOT_KEY, PersistentDataType.STRING)).thenReturn(activeSlot);
        when(pdc.get(TRIGGER_BINDINGS_KEY, PersistentDataType.STRING)).thenReturn(bindings);
        return pdc;
    }
}
