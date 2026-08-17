package emaki.jiuwu.craft.strengthen.enhancement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.math.CraftRollEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.variable.VariableContext;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;
import emaki.jiuwu.craft.strengthen.enhancement.cost.ConsumeTimingEnum;
import emaki.jiuwu.craft.strengthen.enhancement.cost.CurrencyConfig;
import emaki.jiuwu.craft.strengthen.enhancement.cost.MaterialSlotConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.InMemoryPityStateStore;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityDecayTypeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityEffectTypeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityScopeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityState;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipe;
import emaki.jiuwu.craft.strengthen.enhancement.target.EnhancementTargetRegistry;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;

/**
 * 执行强化框架配方的服务。
 *
 * <p>把 {@link EnhancementRecipe} 的六段配置跑成一次实际强化：解析目标 Provider、校验材料、
 * 计算成功率（含保底）、判定、按 {@link ConsumeTimingEnum} 扣除消耗、通过 Provider 写回结果。
 *
 * <p>与既有 {@code StrengthenAttemptService} 的分工：后者是整件星级强化的专用流程（含 journal、
 * 补偿事务、物品重建）；本服务是配方驱动的通用流程，目标类型由 Provider 决定。两者刻意不共用
 * 执行链——星级强化的事务语义比配方流程重，强行合并会把补偿逻辑带进所有目标类型。
 *
 * <p><strong>线程：</strong>玩家所属实体线程。本服务不自行调度。
 */
public final class EnhancementAttemptService {

    private final EmakiStrengthenPlugin plugin;
    private final EnhancementTargetRegistry targetRegistry;
    private final InMemoryPityStateStore pityStateStore;

    public EnhancementAttemptService(EmakiStrengthenPlugin plugin,
            EnhancementTargetRegistry targetRegistry,
            InMemoryPityStateStore pityStateStore) {
        this.plugin = plugin;
        this.targetRegistry = targetRegistry;
        this.pityStateStore = pityStateStore;
    }

    /**
     * 执行一次强化尝试。
     *
     * @param player   发起玩家
     * @param recipe   使用的配方
     * @param target   待强化物品，成功时就地写回
     * @param supplied 玩家提供的材料（GUI 槽位内容），可为空
     * @return 执行结果；未提交时不会改动物品也不会扣费
     */
    public @NotNull EnhancementAttemptResult attempt(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied) {
        if (player == null || !player.isOnline()) {
            return EnhancementAttemptResult.rejected("strengthen.enhancement.no_player");
        }
        if (recipe == null) {
            return EnhancementAttemptResult.rejected("strengthen.error.no_recipe");
        }
        if (target == null || target.getType().isAir()) {
            return EnhancementAttemptResult.rejected("strengthen.error.no_target");
        }
        EnhancementTargetProvider provider = resolveProvider(recipe, target);
        if (provider == null) {
            return EnhancementAttemptResult.rejected("strengthen.enhancement.provider_not_found",
                    Map.of("provider", recipe.target().provider()));
        }
        return execute(player, recipe, target, supplied == null ? List.of() : supplied, provider);
    }

    /**
     * 解析配方声明的目标 Provider。
     *
     * <p>刻意按 {@code target.provider} 精确取用，而不是让注册中心用 {@code canHandle} 猜：内置
     * equipment provider 对任何非空气物品都返回 true，一旦走猜测路径就会遮蔽 gem / affix 等其他
     * 目标类型。同时仍要求该 Provider 认领这件物品，避免把宝石配方套在普通装备上。
     */
    private @Nullable EnhancementTargetProvider resolveProvider(EnhancementRecipe recipe, ItemStack target) {
        if (targetRegistry == null) {
            return null;
        }
        String providerId = recipe.target().provider();
        EnhancementTargetProvider provider = Texts.isBlank(providerId) ? null : targetRegistry.get(providerId);
        if (provider == null) {
            return null;
        }
        try {
            return provider.canHandle(target) ? provider : null;
        } catch (RuntimeException | LinkageError exception) {
            warn("目标 Provider '" + providerId + "' 的 canHandle 抛出异常", exception);
            return null;
        }
    }

    private void warn(String message, Throwable throwable) {
        if (plugin != null && plugin.getLogger() != null) {
            plugin.getLogger().warning(message + ": "
                    + (throwable == null ? "unknown" : throwable.getMessage()));
        }
    }

    private EnhancementAttemptResult execute(Player player,
            EnhancementRecipe recipe,
            ItemStack target,
            List<ItemStack> supplied,
            EnhancementTargetProvider provider) {
        int currentLevel = provider.readLevel(target);
        int currentTemper = provider.readTemper(target);
        VariableContext variables = buildVariables(player, currentLevel, currentTemper);

        List<MaterialMatch> materialMatches = matchMaterials(recipe, player, target, supplied, variables);
        if (materialMatches == null) {
            return EnhancementAttemptResult.rejected("strengthen.error.material_missing");
        }

        PityView pity = loadPity(recipe, player, target, provider);
        double baseRate = CraftRollEngine.clamp(recipe.chance().resolve(variables));
        double effectiveRate = baseRate;
        boolean forceSuccess = false;
        if (pity.triggered()) {
            PityEffectTypeEnum effectType = recipe.pity().effect().type();
            if (effectType == PityEffectTypeEnum.FORCE_SUCCESS) {
                forceSuccess = true;
                effectiveRate = 1D;
            } else if (effectType == PityEffectTypeEnum.CHANCE_BONUS) {
                Double bonus = recipe.pity().effect().bonusValue();
                effectiveRate = CraftRollEngine.clamp(baseRate + (bonus == null ? 0D : bonus));
            }
        }

        boolean success = forceSuccess || CraftRollEngine.roll(effectiveRate);

        // 扣费在判定之后、写回之前：ConsumeTimingEnum 需要知道成败才能决定是否消耗。
        if (!chargeCurrencies(player, recipe, variables)) {
            return EnhancementAttemptResult.rejected("strengthen.error.insufficient_funds");
        }
        consumeMaterials(materialMatches, success);

        int resultingLevel = success ? currentLevel + 1 : currentLevel;
        if (success) {
            try {
                provider.writeLevel(target, resultingLevel);
            } catch (RuntimeException | LinkageError exception) {
                warn("目标 Provider '" + provider.id() + "' 写回等级失败", exception);
                return EnhancementAttemptResult.rejected("strengthen.error.rebuild_failed");
            }
        }

        PityView updated = updatePity(recipe, pity, success);
        return new EnhancementAttemptResult(true, success, "", Map.of(),
                currentLevel, resultingLevel, effectiveRate, updated.counter(), pity.triggered());
    }

    private VariableContext buildVariables(Player player, int level, int temper) {
        return VariableContext.builder(player)
                .with("target.level", level)
                .with("target.temper", temper)
                // 同时暴露下划线形式：公式里两种写法都很常见，只支持一种会让配置方反复踩空。
                .with("target_level", level)
                .with("target_temper", temper)
                .build();
    }

    /**
     * 把配方的每个材料槽匹配到玩家提供的物品上。
     *
     * <p>返回 {@code null} 表示有材料槽未被满足，调用方必须整体拒绝——不能只扣一部分材料。
     * 同一件提供物不会被两个槽位重复计数。
     */
    private @Nullable List<MaterialMatch> matchMaterials(EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            List<ItemStack> supplied,
            VariableContext variables) {
        if (recipe.materials().isEmpty()) {
            return List.of();
        }
        List<MaterialMatch> matches = new ArrayList<>();
        // 记录每件提供物已被占用的数量，避免一件物品同时满足多个槽位。
        Map<ItemStack, Integer> consumedPerStack = new LinkedHashMap<>();
        for (MaterialSlotConfig slot : recipe.materials()) {
            int required = Math.max(0, slot.quantity().resolveInt(variables));
            if (required == 0) {
                continue;
            }
            int remaining = required;
            List<MaterialMatch> slotMatches = new ArrayList<>();
            for (ItemStack candidate : supplied) {
                if (candidate == null || candidate.getType().isAir() || remaining <= 0) {
                    continue;
                }
                int alreadyUsed = consumedPerStack.getOrDefault(candidate, 0);
                int available = candidate.getAmount() - alreadyUsed;
                if (available <= 0 || !testMatcher(slot, candidate, target, player, variables)) {
                    continue;
                }
                int take = Math.min(available, remaining);
                slotMatches.add(new MaterialMatch(candidate, take, slot.consumeTiming()));
                consumedPerStack.merge(candidate, take, Integer::sum);
                remaining -= take;
            }
            if (remaining > 0) {
                return null;
            }
            matches.addAll(slotMatches);
        }
        return List.copyOf(matches);
    }

    private boolean testMatcher(MaterialSlotConfig slot,
            ItemStack candidate,
            ItemStack target,
            Player player,
            VariableContext variables) {
        try {
            MatchContext context = new MatchContext(candidate, null, player, target, null, variables);
            return slot.matcher().test(context);
        } catch (RuntimeException | LinkageError exception) {
            warn("材料 Matcher 判定抛出异常，视为不匹配", exception);
            return false;
        }
    }

    /** 按 {@link ConsumeTimingEnum} 实际扣减材料数量。 */
    private void consumeMaterials(List<MaterialMatch> matches, boolean success) {
        for (MaterialMatch match : matches) {
            if (!shouldConsume(match.timing(), success)) {
                continue;
            }
            ItemStack stack = match.stack();
            int remaining = stack.getAmount() - match.amount();
            if (remaining <= 0) {
                stack.setAmount(0);
            } else {
                stack.setAmount(remaining);
            }
        }
    }

    private static boolean shouldConsume(ConsumeTimingEnum timing, boolean success) {
        return switch (timing) {
            case ALWAYS -> true;
            case SUCCESS -> success;
            case FAILURE -> !success;
            case NEVER -> false;
        };
    }

    /**
     * 通过既有 {@code StrengthenEconomyService} 扣除货币。
     *
     * <p>刻意复用而不另写一套：该服务已实现多货币聚合、余额校验与失败补偿，重写会让两条强化路径
     * 的扣费语义分叉。{@link CurrencyConfig} 逐条映射为 {@code AttemptCost}。
     */
    private boolean chargeCurrencies(Player player, EnhancementRecipe recipe, VariableContext variables) {
        if (recipe.costs().isEmpty()) {
            return true;
        }
        StrengthenEconomyService economy = plugin == null ? null : plugin.economyService();
        if (economy == null) {
            return false;
        }
        List<AttemptCost> costs = new ArrayList<>();
        for (CurrencyConfig currency : recipe.costs()) {
            long amount = Math.max(0L, currency.amount().resolveLong(variables));
            if (amount <= 0L) {
                continue;
            }
            costs.add(new AttemptCost(currency.provider(), currency.currencyId(), currency.currencyId(), amount));
        }
        if (costs.isEmpty()) {
            return true;
        }
        try {
            return economy.charge(player, costs).success();
        } catch (RuntimeException | LinkageError exception) {
            warn("强化框架扣费失败", exception);
            return false;
        }
    }

    /** 读取当前保底状态，并判断本次是否已达触发条件。 */
    private PityView loadPity(EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            EnhancementTargetProvider provider) {
        EnhancementRecipe.PityConfig pity = recipe.pity();
        if (pity == null || pityStateStore == null) {
            return new PityView(null, null, null, 0, false);
        }
        PityScopeEnum scope = pity.counter().scope();
        String group = pity.counter().group();
        String key = pityKey(scope, player, target, provider);
        if (Texts.isBlank(key)) {
            return new PityView(null, null, null, 0, false);
        }
        PityState state = pityStateStore.load(scope.name(), group, key);
        int counter = state == null ? 0 : state.getCounter();
        Integer threshold = pity.trigger().threshold();
        boolean triggered = threshold != null && counter >= threshold;
        return new PityView(scope, group, key, counter, triggered);
    }

    /**
     * 按成败推进保底计数。
     *
     * <p>失败累加，成功按 {@code decay} 规则衰减。{@code decay} 缺省视为 RESET，与解析层默认一致。
     */
    private PityView updatePity(EnhancementRecipe recipe, PityView pity, boolean success) {
        if (pity.scope() == null || pityStateStore == null) {
            return pity;
        }
        int counter = pity.counter();
        if (success) {
            PityDecayTypeEnum decayType = recipe.pity().decay() == null
                    ? PityDecayTypeEnum.RESET
                    : recipe.pity().decay().type();
            double decayValue = recipe.pity().decay() == null ? 0D : recipe.pity().decay().value();
            counter = switch (decayType) {
                case RESET -> 0;
                case FIXED_DECAY -> Math.max(0, counter - (int) Math.round(decayValue));
                case PROPORTIONAL -> Math.max(0, (int) Math.floor(counter * (1D - decayValue)));
            };
        } else {
            counter = counter + 1;
        }
        PityState next = new PityState(counter, System.currentTimeMillis(), pity.triggered());
        pityStateStore.save(pity.scope().name(), pity.group(), pity.key(), next);
        return new PityView(pity.scope(), pity.group(), pity.key(), counter, pity.triggered());
    }

    /**
     * 解析保底计数的定位键。
     *
     * <p>item 作用域用 Provider 读到的配方 ID 加等级无法唯一定位一件物品，因此改用玩家 UUID 作为
     * 退化键并在此说明：真正的按物品保底需要目标物品上有稳定实例 ID（如宝石的 instance_id），
     * 待 Provider 能统一暴露实例标识后再收紧。
     */
    private String pityKey(PityScopeEnum scope,
            Player player,
            ItemStack target,
            EnhancementTargetProvider provider) {
        if (scope == PityScopeEnum.PLAYER) {
            return player.getUniqueId().toString();
        }
        String recipeId = provider.readRecipeId(target);
        return Texts.isBlank(recipeId)
                ? player.getUniqueId().toString()
                : player.getUniqueId() + ":" + recipeId;
    }

    private record MaterialMatch(ItemStack stack, int amount, ConsumeTimingEnum timing) {
    }

    private record PityView(@Nullable PityScopeEnum scope,
            @Nullable String group,
            @Nullable String key,
            int counter,
            boolean triggered) {
    }
}
