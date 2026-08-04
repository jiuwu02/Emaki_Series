package emaki.jiuwu.craft.gem.papi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.placeholder.AbstractEmakiPlaceholderExpansion;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.loader.GemItemLoader;
import emaki.jiuwu.craft.gem.loader.GemLoader;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemResonanceDefinition;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.service.GemResonanceService;
import emaki.jiuwu.craft.gem.service.GemStateService;

public final class GemPlaceholderExpansion extends AbstractEmakiPlaceholderExpansion {

    private static final String PREFIX = "mainhand_";

    private final EmakiGemPlugin plugin;
    private final GemStateService stateService;
    private final GemItemLoader gemItemLoader;

    public GemPlaceholderExpansion(EmakiGemPlugin plugin, GemStateService stateService, GemItemLoader gemItemLoader) {
        super(plugin);
        this.plugin = plugin;
        this.stateService = stateService;
        this.gemItemLoader = gemItemLoader;
    }

    @Override
    public String getIdentifier() {
        return "emakigem";
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
        if ("item_definition_id".equals(key)) {
            return state == null ? "" : Texts.toStringSafe(state.itemDefinitionId());
        }
        if ("resonance_count".equals(key)) {
            return String.valueOf(countActiveResonances(state));
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

    private int countActiveResonances(GemState state) {
        if (state == null || state.socketAssignments().isEmpty()) {
            return 0;
        }
        GemResonanceService resonanceService = plugin.resonanceService();
        GemLoader gemLoader = plugin.gemLoader();
        if (resonanceService == null || gemLoader == null) {
            return 0;
        }
        Map<String, GemDefinition> allGems = gemLoader.all();
        List<GemDefinition> inlaidGems = new ArrayList<>();
        for (GemItemInstance instance : state.socketAssignments().values()) {
            if (instance == null || Texts.isBlank(instance.gemId())) {
                continue;
            }
            GemDefinition def = allGems.get(instance.gemId());
            if (def != null) {
                inlaidGems.add(def);
            }
        }
        if (inlaidGems.isEmpty()) {
            return 0;
        }
        List<GemResonanceDefinition> active = resonanceService.evaluate(inlaidGems);
        return active == null ? 0 : active.size();
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
            case "type" -> typeAtSlot(state, slotIndex);
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

    private String typeAtSlot(GemState state, int slotIndex) {
        if (state == null) {
            return "";
        }
        GemItemDefinition definition = gemItemLoader.all().get(state.itemDefinitionId());
        if (definition == null) {
            return "";
        }
        GemItemDefinition.SocketSlot slot = definition.slot(slotIndex);
        return slot == null ? "" : slot.type();
    }
}
