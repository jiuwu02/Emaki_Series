package emaki.jiuwu.craft.skills.service;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSourceType;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.provider.SkillSourceProvider;


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
        if (dataStore == null || Texts.isBlank(normalizedSkillId) || !isValidSkill(normalizedSkillId)) {
            return false;
        }
        AtomicBoolean added = new AtomicBoolean();
        dataStore.mutate(player, profile -> {
            added.set(profile.manualSkillIds().add(normalizedSkillId));
            if (added.get()) {
                profile.markDirty();
            }
        });
        return added.get();
    }

    public boolean forget(Player player, String skillId) {
        String normalizedSkillId = Texts.normalizeId(skillId);
        if (dataStore == null || Texts.isBlank(normalizedSkillId)) {
            return false;
        }
        AtomicBoolean removed = new AtomicBoolean();
        dataStore.mutate(player, profile -> {
            removed.set(profile.manualSkillIds().remove(normalizedSkillId));
            if (removed.get()) {
                profile.markDirty();
            }
        });
        return removed.get();
    }

    public int forgetAll(Player player) {
        if (dataStore == null) {
            return 0;
        }
        AtomicInteger removed = new AtomicInteger();
        dataStore.mutate(player, profile -> {
            removed.set(profile.manualSkillIds().size());
            if (removed.get() > 0) {
                profile.manualSkillIds().clear();
                profile.markDirty();
            }
        });
        return removed.get();
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
