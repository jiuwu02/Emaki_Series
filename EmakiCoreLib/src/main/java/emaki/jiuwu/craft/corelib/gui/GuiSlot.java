package emaki.jiuwu.craft.corelib.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.text.Texts;

/** Immutable GUI slot definition with legacy accessors retained for ABI compatibility. */
public final class GuiSlot {

    private final String key;
    private final List<Integer> slots;
    private final String type;
    private final String item;
    private final ItemComponentParser.ItemComponents components;
    private final Map<GuiClickType, SoundParser.SoundDefinition> sounds;
    private final ConfiguredItemDefinition itemDefinition;

    public GuiSlot(String key,
            List<Integer> slots,
            String type,
            String item,
            ItemComponentParser.ItemComponents components,
            Map<GuiClickType, SoundParser.SoundDefinition> sounds) {
        this(
                key,
                slots,
                type,
                item,
                components == null ? ItemComponentParser.empty() : components,
                sounds,
                ItemComponentParser.toDefinition(item, components, 1, Map.of())
        );
    }

    public GuiSlot(String key,
            List<Integer> slots,
            String type,
            ConfiguredItemDefinition itemDefinition,
            Map<GuiClickType, SoundParser.SoundDefinition> sounds) {
        this(
                key,
                slots,
                type,
                itemDefinition == null ? null : itemDefinition.source(),
                ItemComponentParser.fromDefinition(itemDefinition),
                sounds,
                itemDefinition == null ? new ConfiguredItemDefinition(null, 1, Map.of()) : itemDefinition
        );
    }

    private GuiSlot(String key,
            List<Integer> slots,
            String type,
            String item,
            ItemComponentParser.ItemComponents components,
            Map<GuiClickType, SoundParser.SoundDefinition> sounds,
            ConfiguredItemDefinition itemDefinition) {
        this.key = key;
        this.slots = slots == null ? List.of() : List.copyOf(slots);
        this.type = type;
        this.item = item;
        this.components = components == null ? ItemComponentParser.empty() : components;
        this.sounds = sounds == null ? Map.of() : Map.copyOf(sounds);
        this.itemDefinition = itemDefinition == null
                ? new ConfiguredItemDefinition(item, 1, Map.of())
                : itemDefinition;
    }

    public String key() {
        return key;
    }

    public List<Integer> slots() {
        return slots;
    }

    public String type() {
        return type;
    }

    public String item() {
        return item;
    }

    public ItemComponentParser.ItemComponents components() {
        return components;
    }

    public Map<GuiClickType, SoundParser.SoundDefinition> sounds() {
        return sounds;
    }

    public ConfiguredItemDefinition itemDefinition() {
        return itemDefinition;
    }

    public boolean hasType() {
        return Texts.isNotBlank(type);
    }

    public SoundParser.SoundDefinition soundFor(GuiClickType clickType) {
        if (clickType == GuiClickType.LEFTCLICK && sounds.containsKey(GuiClickType.LEFTCLICK)) {
            return sounds.get(GuiClickType.LEFTCLICK);
        }
        if (clickType == GuiClickType.RIGHTCLICK && sounds.containsKey(GuiClickType.RIGHTCLICK)) {
            return sounds.get(GuiClickType.RIGHTCLICK);
        }
        return sounds.get(GuiClickType.CLICK);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GuiSlot slot)) {
            return false;
        }
        return Objects.equals(key, slot.key)
                && slots.equals(slot.slots)
                && Objects.equals(type, slot.type)
                && Objects.equals(item, slot.item)
                && components.equals(slot.components)
                && sounds.equals(slot.sounds)
                && itemDefinition.equals(slot.itemDefinition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, slots, type, item, components, sounds, itemDefinition);
    }

    @Override
    public String toString() {
        return "GuiSlot[key=" + key
                + ", slots=" + slots
                + ", type=" + type
                + ", item=" + item
                + ", components=" + components
                + ", sounds=" + sounds
                + ", itemDefinition=" + itemDefinition + "]";
    }
}
