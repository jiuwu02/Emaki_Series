package emaki.jiuwu.craft.codex.codex.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.attribute.api.extension.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.extension.AttributeContributionProvider;
import emaki.jiuwu.craft.codex.codex.loader.CodexCategoryLoader;
import emaki.jiuwu.craft.codex.codex.model.CodexEntry;
import emaki.jiuwu.craft.codex.codex.model.PlayerCodex;
import emaki.jiuwu.craft.codex.codex.service.PlayerCodexStore;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class CodexAttributeProvider implements AttributeContributionProvider {

    public static final String PROVIDER_ID = "emakicodex:codex_rewards";
    private static final String SOURCE_PREFIX = "emakicodex:";

    private final CodexCategoryLoader categoryLoader;
    private final PlayerCodexStore codexStore;
    private final Logger logger;

    public CodexAttributeProvider(CodexCategoryLoader categoryLoader,
            PlayerCodexStore codexStore,
            Logger logger) {
        this.categoryLoader = categoryLoader;
        this.codexStore = codexStore;
        this.logger = logger;
    }

    @Override
    public @NotNull String id() {
        return PROVIDER_ID;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public @NotNull Collection<AttributeContribution> collect(@NotNull LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return List.of();
        }
        try {
            return contributionsFor(player);
        } catch (RuntimeException exception) {
            logger.warning("Codex attribute contribution failed for " + player.getName()
                    + ": " + Texts.toStringSafe(exception.getMessage()));
            return List.of();
        }
    }

    private Collection<AttributeContribution> contributionsFor(Player player) {
        PlayerCodex codex = codexStore.cached(player.getUniqueId());
        if (codex == null) {
            return List.of();
        }
        List<AttributeContribution> contributions = new ArrayList<>();
        for (String key : codex.activatedKeys()) {
            int separator = key.indexOf('/');
            if (separator <= 0 || separator >= key.length() - 1) {
                continue;
            }
            String categoryId = key.substring(0, separator);
            String entryId = key.substring(separator + 1);
            CodexEntry entry = categoryLoader.entryAt(categoryId, entryId);
            if (entry == null || !entry.hasAttributeRewards()) {
                continue;
            }
            String sourceId = SOURCE_PREFIX + categoryId + "/" + entryId;
            for (Map.Entry<String, Double> reward : entry.attributeRewards().entrySet()) {
                contributions.add(new AttributeContribution(reward.getKey(), reward.getValue(), sourceId));
            }
        }
        return List.copyOf(contributions);
    }
}
