package emaki.jiuwu.craft.kits.gui;

import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiDragContext;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.kits.config.KitIds;
import emaki.jiuwu.craft.kits.model.ChoiceOption;
import emaki.jiuwu.craft.kits.model.ClaimResult;
import emaki.jiuwu.craft.kits.model.KitDefinition;
import emaki.jiuwu.craft.kits.service.KitService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChoiceGuiService {

    private static final String CONFIRM_KEY = "confirm";

    private final Plugin plugin;
    private final GuiService guiService;
    private final GuiTemplateLoader templates;
    private final MessageService messages;
    private final KitService kits;
    private final Map<UUID, SelectionState> selections = new ConcurrentHashMap<>();

    public ChoiceGuiService(Plugin plugin, GuiService guiService, GuiTemplateLoader templates,
                            MessageService messages, KitService kits) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.templates = templates;
        this.messages = messages;
        this.kits = kits;
    }

    public boolean open(Player player, String rawKitId) {
        String kitId = KitIds.normalize(rawKitId);
        KitDefinition def = kits.kit(kitId);
        if (def == null || !def.enabled()
                || def.type() != emaki.jiuwu.craft.kits.model.KitType.CHOICE) {
            messages.send(player, "error.kit.unknown", Map.of("kit", kitId));
            return false;
        }
        GuiTemplate template = def.choice().usesInlineGui()
                ? def.choice().inlineGui()
                : templates.get(def.choice().guiTemplate());
        if (template == null) {
            messages.send(player, "error.choice.gui_missing",
                    Map.of("template", def.choice().guiTemplate()));
            return false;
        }
        SelectionState state = new SelectionState(def);
        selections.put(player.getUniqueId(), state);
        GuiOpenRequest request = new GuiOpenRequest(plugin, player, template,
                Map.of("kit", def.displayName()), null, new Handler(def, state));
        GuiSession session = guiService.open(request);
        return session != null;
    }

    public void closeAll() {
        selections.clear();
    }

    private final class Handler implements GuiSessionHandler {

        private final KitDefinition def;
        private final SelectionState state;

        Handler(KitDefinition def, SelectionState state) {
            this.def = def;
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession session, GuiClickContext click,
                                GuiTemplate.ResolvedSlot slot) {
            if (slot == null || slot.definition() == null) {
                return;
            }
            Player player = session.viewer();
            String key = KitIds.normalize(slot.definition().key());
            if (CONFIRM_KEY.equals(key)) {
                submit(session, player);
                return;
            }
            ChoiceOption option = state.known.get(key);
            if (option == null) {
                return;
            }
            if (option.requiresPermission() && !player.hasPermission(option.permission())) {
                messages.send(player, "error.choice.permission", Map.of("kit", def.id()));
                return;
            }
            if (state.chosen.contains(key)) {
                state.chosen.remove(key);
                messages.send(player, "choice.deselected", Map.of("option", option.display()));
                return;
            }
            if (state.chosen.size() >= def.choice().maxSelect()) {
                messages.send(player, "error.choice.count", Map.of("kit", def.id()));
                return;
            }
            state.chosen.add(key);
            messages.send(player, "choice.selected", Map.of("option", option.display(),
                    "count", state.chosen.size(), "max", def.choice().maxSelect()));
        }

        @Override
        public void onClose(GuiSession session, GuiCloseContext close) {
            selections.remove(session.viewer().getUniqueId());
        }

        private void submit(GuiSession session, Player player) {
            if (state.chosen.size() < def.choice().minSelect()
                    || state.chosen.size() > def.choice().maxSelect()) {
                messages.send(player, "error.choice.count", Map.of("kit", def.id()));
                return;
            }
            ClaimResult result = kits.claim(player, def.id(), List.copyOf(state.chosen));
            Map<String, ?> vars = Map.of("kit", result.kitId(), "period", result.periodKey());
            if (result.success()) {
                messages.send(player, "claim.success", vars);
                session.viewer().closeInventory();
                selections.remove(player.getUniqueId());
            } else {
                messages.send(player, result.reasonKey(), vars);
            }
        }
    }

    private static final class SelectionState {

        final Map<String, ChoiceOption> known = new LinkedHashMap<>();
        final Set<String> chosen = new LinkedHashSet<>();

        SelectionState(KitDefinition def) {
            for (ChoiceOption option : def.choice().options()) {
                known.put(option.optionId(), option);
            }
        }
    }
}
