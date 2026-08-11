package emaki.jiuwu.craft.corelib.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.text.Texts;


public final class GuiSlot {

    private final String key;
    private final List<Integer> slots;
    private final String type;
    private final String item;
    private final Map<GuiClickType, SoundParser.SoundDefinition> sounds;
    private final ConfiguredItemDefinition itemDefinition;

    public GuiSlot(String key,
            List<Integer> slots,
            String type,
            ConfiguredItemDefinition itemDefinition,
            Map<GuiClickType, SoundParser.SoundDefinition> sounds) {
        this.key = key;
        this.slots = slots == null ? List.of() : List.copyOf(slots);
        this.type = type;
        this.item = itemDefinition == null ? null : itemDefinition.source();
        this.sounds = sounds == null ? Map.of() : Map.copyOf(sounds);
        this.itemDefinition = itemDefinition == null
                ? new ConfiguredItemDefinition(null, 1, Map.of())
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

    public Map<GuiClickType, SoundParser.SoundDefinition> sounds() {
        return sounds;
    }

    public ConfiguredItemDefinition itemDefinition() {
        return itemDefinition;
    }

    public boolean hasType() {
        return Texts.isNotBlank(type);
    }

    /**
     * {@return whether the template configured any item component for this slot} Renderers use this
     * to decide between the template's own styling and their code-side fallback name/lore.
     */
    public boolean hasConfiguredComponents() {
        return !itemDefinition.components().isEmpty();
    }

    public SoundParser.SoundDefinition soundFor(GuiClickType clickType) {
        if (clickType == null) {
            return sounds.get(GuiClickType.CLICK);
        }
        if (clickType != GuiClickType.CLICK && sounds.containsKey(clickType)) {
            return sounds.get(clickType);
        }
        GuiClickType legacy = clickType.legacyFallback();
        if (legacy != null && sounds.containsKey(legacy)) {
            return sounds.get(legacy);
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
                && sounds.equals(slot.sounds)
                && itemDefinition.equals(slot.itemDefinition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, slots, type, item, sounds, itemDefinition);
    }

    @Override
    public String toString() {
        return "GuiSlot[key=" + key
                + ", slots=" + slots
                + ", type=" + type
                + ", item=" + item
                + ", sounds=" + sounds
                + ", itemDefinition=" + itemDefinition + "]";
    }
}
