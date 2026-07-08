package emaki.jiuwu.craft.skills.service;

import java.util.Collection;
import java.util.List;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSourceType;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.provider.SkillSourceProvider;

/** Provides manually learned skills through the normal SkillSourceRegistry boundary. */
public final class ManualSkillSourceService implements SkillSourceProvider {

    private static final String SOURCE_ID = "manual";

    private final PlayerSkillDataStore dataStore;
    private final SkillRegistryService registryService;

    public ManualSkillSourceService(PlayerSkillDataStore dataStore, SkillRegistryService registryService) {
        this.dataStore = dataStore;
        this.registryService = registryService;
    }

    @Override
    public String id() {
        return SOURCE_ID;
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public Collection<UnlockedSkillEntry> collect(Player player) {
        PlayerSkillProfile profile = dataStore == null ? null : dataStore.get(player);
        if (profile == null || profile.manualSkillIds().isEmpty()) {
            return List.of();
        }
        return profile.manualSkillIds().stream()
                .filter(this::isValidSkill)
                .map(skillId -> new UnlockedSkillEntry(skillId, SOURCE_ID, SkillSourceType.MANUAL, null, null))
                .toList();
    }

    public boolean learn(Player player, String skillId) {
        String normalizedSkillId = Texts.normalizeId(skillId);
        PlayerSkillProfile profile = dataStore == null ? null : dataStore.get(player);
        if (profile == null || Texts.isBlank(normalizedSkillId) || !isValidSkill(normalizedSkillId)) {
            return false;
        }
        boolean added = profile.manualSkillIds().add(normalizedSkillId);
        if (added) {
            profile.markDirty();
        }
        return added;
    }

    public boolean forget(Player player, String skillId) {
        String normalizedSkillId = Texts.normalizeId(skillId);
        PlayerSkillProfile profile = dataStore == null ? null : dataStore.get(player);
        if (profile == null || Texts.isBlank(normalizedSkillId)) {
            return false;
        }
        boolean removed = profile.manualSkillIds().remove(normalizedSkillId);
        if (removed) {
            profile.markDirty();
        }
        return removed;
    }

    public int forgetAll(Player player) {
        PlayerSkillProfile profile = dataStore == null ? null : dataStore.get(player);
        if (profile == null || profile.manualSkillIds().isEmpty()) {
            return 0;
        }
        int removed = profile.manualSkillIds().size();
        profile.manualSkillIds().clear();
        profile.markDirty();
        return removed;
    }

    public boolean hasLearned(Player player, String skillId) {
        String normalizedSkillId = Texts.normalizeId(skillId);
        PlayerSkillProfile profile = dataStore == null ? null : dataStore.get(player);
        return profile != null && Texts.isNotBlank(normalizedSkillId) && profile.manualSkillIds().contains(normalizedSkillId);
    }

    private boolean isValidSkill(String skillId) {
        SkillDefinition definition = registryService == null ? null : registryService.getDefinition(skillId);
        return definition != null && definition.enabled();
    }
}
