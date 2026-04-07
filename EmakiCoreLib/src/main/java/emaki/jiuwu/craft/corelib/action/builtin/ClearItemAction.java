package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ClearItemAction extends BaseAction {

    private final ItemSourceService itemSourceService;

    public ClearItemAction(ItemSourceService itemSourceService) {
        super(
                "clearitem",
                "item",
                "Clear a player inventory slot, optionally filtering by item source.",
                ActionParameter.required("slot", ActionParameterType.STRING, "Inventory slot"),
                ActionParameter.optional("source", ActionParameterType.STRING, "", "Expected item source")
        );
        this.itemSourceService = itemSourceService;
    }

    @Override
    public boolean acceptsDynamicParameter(String name) {
        String normalized = Texts.lower(name);
        return "item".equals(normalized) || "item_source".equals(normalized);
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        SlotRef slotRef = SlotRef.parse(arguments.get("slot"));
        if (slotRef == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unsupported clearitem slot: " + arguments.get("slot"));
        }
        Player player = context.player();
        ItemStack current = slotRef.get(player.getInventory());
        if (current == null || current.getType().isAir()) {
            return ActionResult.skipped("No item present in slot '" + slotRef.id() + "'.");
        }
        String requestedSource = resolveSourceText(arguments);
        ItemSource expected = Texts.isBlank(requestedSource) ? null : ItemSourceUtil.parse(requestedSource);
        if (Texts.isNotBlank(requestedSource) && expected == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid clearitem source: " + requestedSource);
        }
        if (expected != null) {
            ItemSource actual = itemSourceService == null ? null : itemSourceService.identifyItem(current);
            if (!ItemSourceUtil.matches(expected, actual)) {
                return ActionResult.skipped("Item in slot '" + slotRef.id() + "' did not match the requested source.");
            }
        }
        slotRef.clear(player.getInventory());
        return ActionResult.ok(Map.of("slot", slotRef.id()));
    }

    private String resolveSourceText(Map<String, String> arguments) {
        String raw = stringArg(arguments, "source");
        if (Texts.isBlank(raw)) {
            raw = stringArg(arguments, "item");
        }
        if (Texts.isBlank(raw)) {
            raw = stringArg(arguments, "item_source");
        }
        return raw;
    }

    private interface SlotRef {

        String id();

        ItemStack get(PlayerInventory inventory);

        void clear(PlayerInventory inventory);

        static SlotRef parse(String raw) {
            if (Texts.isBlank(raw)) {
                return null;
            }
            String normalized = Texts.trim(raw).toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "mainhand", "main_hand", "hand" -> named("mainhand", PlayerInventory::getItemInMainHand, inventory -> inventory.setItemInMainHand(new ItemStack(Material.AIR)));
                case "offhand", "off_hand" -> named("offhand", PlayerInventory::getItemInOffHand, inventory -> inventory.setItemInOffHand(new ItemStack(Material.AIR)));
                case "helmet" -> named("helmet", PlayerInventory::getHelmet, inventory -> inventory.setHelmet(null));
                case "chestplate", "chest" -> named("chestplate", PlayerInventory::getChestplate, inventory -> inventory.setChestplate(null));
                case "leggings", "legs" -> named("leggings", PlayerInventory::getLeggings, inventory -> inventory.setLeggings(null));
                case "boots" -> named("boots", PlayerInventory::getBoots, inventory -> inventory.setBoots(null));
                default -> indexed(normalized);
            };
        }

        private static SlotRef named(String id, Getter getter, Clearer clearer) {
            return new SlotRef() {
                @Override
                public String id() {
                    return id;
                }

                @Override
                public ItemStack get(PlayerInventory inventory) {
                    return inventory == null ? null : getter.get(inventory);
                }

                @Override
                public void clear(PlayerInventory inventory) {
                    if (inventory != null) {
                        clearer.clear(inventory);
                    }
                }
            };
        }

        private static SlotRef indexed(String raw) {
            String numeric = raw.startsWith("slot_") ? raw.substring("slot_".length()) : raw;
            if (raw.startsWith("hotbar_")) {
                numeric = raw.substring("hotbar_".length());
            }
            Integer index = ActionParsers.parseIntNullable(numeric);
            if (index == null || index < 0 || index > 35) {
                return null;
            }
            final int resolvedIndex = index;
            return new SlotRef() {
                @Override
                public String id() {
                    return "slot_" + resolvedIndex;
                }

                @Override
                public ItemStack get(PlayerInventory inventory) {
                    return inventory == null ? null : inventory.getItem(resolvedIndex);
                }

                @Override
                public void clear(PlayerInventory inventory) {
                    if (inventory != null) {
                        inventory.setItem(resolvedIndex, null);
                    }
                }
            };
        }
    }

    @FunctionalInterface
    private interface Getter {

        ItemStack get(PlayerInventory inventory);
    }

    @FunctionalInterface
    private interface Clearer {

        void clear(PlayerInventory inventory);
    }
}
