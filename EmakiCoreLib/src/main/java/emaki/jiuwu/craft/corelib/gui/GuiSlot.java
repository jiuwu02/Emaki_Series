package emaki.jiuwu.craft.corelib.gui;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.List;
import java.util.Map;

public record GuiSlot(String key,
                      List<Integer> slots,
                      String type,
                      String item,
                      ItemComponentParser.ItemComponents components,
                      Map<GuiClickType, SoundParser.SoundDefinition> sounds) {

    public GuiSlot {
        slots = slots == null ? List.of() : List.copyOf(slots);
        components = components == null ? ItemComponentParser.empty() : components;
        sounds = sounds == null ? Map.of() : Map.copyOf(sounds);
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
}
