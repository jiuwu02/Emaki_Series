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

public final class AccessorySkillProvider implements SkillSourceProvider {

    private static final String PROVIDER_ID = "accessories";

    private final AccessoryContributionService contributionService;
    private final Logger logger;

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
