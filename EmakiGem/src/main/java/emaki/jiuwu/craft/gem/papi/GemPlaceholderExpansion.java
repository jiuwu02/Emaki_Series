package emaki.jiuwu.craft.gem.papi;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.loader.GemItemLoader;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.service.GemStateService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public final class GemPlaceholderExpansion extends PlaceholderExpansion {

    private static final String PREFIX = "mainhand_";

    private final EmakiGemPlugin plugin;
    private final GemStateService stateService;
    private final GemItemLoader gemItemLoader;

    public GemPlaceholderExpansion(EmakiGemPlugin plugin, GemStateService stateService, GemItemLoader gemItemLoader) {
        this.plugin = plugin;
        this.stateService = stateService;
        this.gemItemLoader = gemItemLoader;
    }

    @Override
    public String getIdentifier() {
        return "emakigem";
    }

    @Override
    public String getAuthor() {
        return "Emaki";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || Texts.isBlank(params)) {
            return "";
        }
        String normalized = params.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(PREFIX)) {
            return "";
        }
        String key = normalized.substring(PREFIX.length());
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        GemState state = stateService.resolveState(itemStack);

        if ("opened_slots".equals(key)) {
            return state == null ? "0" : String.valueOf(state.openedSlotIndexes().size());
        }
        if ("total_slots".equals(key)) {
            return String.valueOf(totalSlots(state));
        }
        if ("filled_slots".equals(key)) {
            return state == null ? "0" : String.valueOf(state.socketAssignments().size());
        }
        return slotPlaceholder(state, key);
    }

    private int totalSlots(GemState state) {
        if (state == null) {
            return 0;
        }
        GemItemDefinition definition = gemItemLoader.all().get(state.itemDefinitionId());
        return definition == null ? 0 : definition.slots().size();
    }

    private String slotPlaceholder(GemState state, String key) {
        if (!key.startsWith("slot_")) {
            return "";
        }
        String remainder = key.substring("slot_".length());
        int underscoreIndex = remainder.lastIndexOf('_');
        if (underscoreIndex < 1) {
            return "";
        }
        String indexPart = remainder.substring(0, underscoreIndex);
        String field = remainder.substring(underscoreIndex + 1);
        Integer slotIndex = Numbers.tryParseInt(indexPart, null);
        if (slotIndex == null) {
            return "";
        }
        return switch (field) {
            case "gem" -> gemAtSlot(state, slotIndex);
            case "level" -> levelAtSlot(state, slotIndex);
            case "opened" -> openedAtSlot(state, slotIndex);
            default -> "";
        };
    }

    private String gemAtSlot(GemState state, int slotIndex) {
        if (state == null) {
            return "";
        }
        GemItemInstance instance = state.socketAssignments().get(slotIndex);
        return instance == null ? "" : instance.gemId();
    }

    private String levelAtSlot(GemState state, int slotIndex) {
        if (state == null) {
            return "0";
        }
        GemItemInstance instance = state.socketAssignments().get(slotIndex);
        return instance == null ? "0" : String.valueOf(instance.level());
    }

    private String openedAtSlot(GemState state, int slotIndex) {
        if (state == null) {
            return "false";
        }
        return state.openedSlotIndexes().contains(slotIndex) ? "true" : "false";
    }
}
