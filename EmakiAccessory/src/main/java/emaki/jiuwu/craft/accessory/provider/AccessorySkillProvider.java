package emaki.jiuwu.craft.accessory.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.accessory.model.AccessoryContributionSnapshot;
import emaki.jiuwu.craft.accessory.service.AccessoryContributionService;
import emaki.jiuwu.craft.skills.api.SkillSourceEntry;
import emaki.jiuwu.craft.skills.api.SkillSourceProvider;

/**
 * Feeds accessory-granted skills into EmakiSkills.
 *
 * <p>Like the attribute provider, this only reads a prebuilt snapshot: EmakiSkills calls it synchronously
 * on the player's owner thread while collecting unlocked skills, so it must stay cheap.
 *
 * <p>{@code sourceSlot} reports the slot instance rather than the part, because EmakiSkills produces one
 * entry per source and only an instance id can distinguish which ring granted a skill.
 *
 * <p>Two behaviours follow from EmakiSkills' own semantics and are documented for server owners rather
 * than worked around here: skills are de-duplicated by skill id, so two identical accessories unlock a
 * skill once while their attributes still stack; and equipment sources are collected before providers,
 * so a skill available from both a weapon and an accessory is attributed to the weapon slot. Neither
 * affects whether the skill is usable.
 */
public final class AccessorySkillProvider implements SkillSourceProvider {

    private static final String PROVIDER_ID = "accessories";

    private final AccessoryContributionService contributionService;
    private final Logger logger;

    /**
     * Creates the provider.
     *
     * @param contributionService the snapshot cache to read
     * @param logger              receives the one-line report when a collection fails
     */
    public AccessorySkillProvider(AccessoryContributionService contributionService, Logger logger) {
        this.contributionService = contributionService;
        this.logger = logger;
    }

    @Override
    public @NotNull String id() {
        return PROVIDER_ID;
    }

    @Override
    public @NotNull Collection<SkillSourceEntry> collect(@NotNull Player player) {
        try {
            AccessoryContributionSnapshot snapshot = contributionService.snapshot(player.getUniqueId());
            Map<String, String> skills = snapshot.skills();
            if (skills.isEmpty()) {
                return List.of();
            }
            List<SkillSourceEntry> entries = new ArrayList<>(skills.size());
            skills.forEach((skillId, sourceSlot) ->
                    entries.add(new SkillSourceEntry(skillId, sourceSlot, "")));
            return entries;
        } catch (RuntimeException exception) {
            if (logger != null) {
                logger.warning("Accessory skill collection failed: " + exception.getMessage());
            }
            return List.of();
        }
    }
}
