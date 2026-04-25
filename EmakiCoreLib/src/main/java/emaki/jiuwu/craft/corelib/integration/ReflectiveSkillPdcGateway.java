package emaki.jiuwu.craft.corelib.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ReflectiveSkillPdcGateway {

    private static final NamespacedKey SKILL_IDS_KEY = new NamespacedKey("emaki_skills", "item.skills.ids");

    public void write(ItemStack itemStack, Collection<String> skillIds) {
        if (itemStack == null) {
            return;
        }
        List<String> normalized = normalize(skillIds);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        if (normalized.isEmpty()) {
            itemMeta.getPersistentDataContainer().remove(SKILL_IDS_KEY);
        } else {
            itemMeta.getPersistentDataContainer().set(SKILL_IDS_KEY, PersistentDataType.STRING, String.join(";", normalized));
        }
        itemStack.setItemMeta(itemMeta);
    }

    public void clear(ItemStack itemStack) {
        write(itemStack, List.of());
    }

    public void copy(ItemStack original, ItemStack rebuilt) {
        if (original == null || rebuilt == null) {
            return;
        }
        ItemMeta originalMeta = original.getItemMeta();
        ItemMeta rebuiltMeta = rebuilt.getItemMeta();
        if (originalMeta == null || rebuiltMeta == null) {
            return;
        }
        String raw = originalMeta.getPersistentDataContainer().get(SKILL_IDS_KEY, PersistentDataType.STRING);
        if (Texts.isBlank(raw)) {
            rebuiltMeta.getPersistentDataContainer().remove(SKILL_IDS_KEY);
        } else {
            rebuiltMeta.getPersistentDataContainer().set(SKILL_IDS_KEY, PersistentDataType.STRING, raw);
        }
        rebuilt.setItemMeta(rebuiltMeta);
    }

    private List<String> normalize(Collection<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String skillId : skillIds) {
            String normalized = Texts.normalizeId(skillId);
            if (Texts.isNotBlank(normalized)) {
                values.add(normalized);
            }
        }
        List<String> result = new ArrayList<>(values);
        result.sort(String::compareTo);
        return result;
    }
}
