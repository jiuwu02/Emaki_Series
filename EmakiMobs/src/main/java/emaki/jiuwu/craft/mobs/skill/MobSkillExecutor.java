package emaki.jiuwu.craft.mobs.skill;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionEngine;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * 编译并执行 mob 技能管道。
 *
 * <p>每个 mob 类型的技能在首次触发时按触发器维度编译为 {@link CompiledPipeline} 并缓存。
 * reload 时应调用 {@link #invalidate()} 清空缓存，使下次触发重新编译最新配置。
 *
 * <p>若 EmakiCoreLib 未就绪或技能 YAML 存在语法错误，该触发器的管道静默跳过，
 * 编译错误以 WARNING 写入服务器日志。
 */
public final class MobSkillExecutor {

    /** 缓存：mob_id → (触发器名 → 已编译管道列表) */
    private final Map<String, Map<String, List<CompiledPipeline>>> cache = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final Supplier<Map<String, MobSpec>> mobRegistry;
    private final Logger logger;

    public MobSkillExecutor(Plugin plugin,
                            Supplier<Map<String, MobSpec>> mobRegistry,
                            Logger logger) {
        this.plugin = plugin;
        this.mobRegistry = mobRegistry;
        this.logger = logger;
    }

    /**
     * 对指定实体触发某个技能触发器，异步运行其所有 Action 管道行。
     *
     * @param mob     触发技能的生物实体（作为施法者 / caster）
     * @param mobId   生物定义 ID（来自 PDC）
     * @param trigger 触发器名称，如 {@code on_death}、{@code on_damage_give}
     */
    public void executeForTrigger(LivingEntity mob, String mobId, String trigger) {
        executeForTrigger(mob, mobId, trigger, Map.of());
    }

    /**
     * 对指定实体触发某个技能触发器，并传递上下文数据。
     *
     * @param mob           触发技能的生物实体（作为施法者 / caster）
     * @param mobId         生物定义 ID（来自 PDC）
     * @param trigger       触发器名称，如 {@code on_death}、{@code on_damage_give}
     * @param contextData   上下文数据（如 attacker、killer、victim 等）
     */
    public void executeForTrigger(LivingEntity mob, String mobId, String trigger,
                                   @Nullable Map<CoreActionKey<?>, Object> contextData) {
        ActionEngine engine = engine();
        if (engine == null) {
            return;
        }
        MobSpec spec = mobRegistry.get().get(mobId);
        if (spec == null || spec.skills().isEmpty()) {
            return;
        }
        Map<String, List<CompiledPipeline>> triggerMap =
                cache.computeIfAbsent(mobId, id -> compileMobSkills(engine, id, spec));
        List<CompiledPipeline> pipelines = triggerMap.get(trigger);
        if (pipelines == null || pipelines.isEmpty()) {
            return;
        }
        CoreActionSubject caster = CoreActionSubject.of(mob);
        PipelineContext context = PipelineContext.root(
                plugin, caster, mob.getLocation(), trigger, false, null);
        
        // 注入上下文数据
        if (contextData != null && !contextData.isEmpty()) {
            context = context.withData(contextData);
        }
        
        for (CompiledPipeline pipeline : pipelines) {
            engine.run(plugin, pipeline, context);
        }
    }

    /**
     * 清空已编译管道缓存。应在 mob 配置重载后调用，以便重新编译最新 YAML。
     */
    public void invalidate() {
        cache.clear();
    }

    // ── 私有方法 ────────────────────────────────────────────────────────────

    private Map<String, List<CompiledPipeline>> compileMobSkills(ActionEngine engine,
                                                                   String mobId,
                                                                   MobSpec spec) {
        Map<String, List<CompiledPipeline>> result = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : spec.skills().entrySet()) {
            String trigger = entry.getKey();
            List<String> lines = entry.getValue();
            if (lines.isEmpty()) {
                continue;
            }
            List<CompiledPipeline> compiled = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                ActionEngine.Result r = engine.compile(lines.get(i), null);
                if (r.successful()) {
                    compiled.add(r.pipeline());
                } else {
                    int lineNum = i + 1;
                    logger.warning("[EmakiMobs] 技能编译错误: mob='" + mobId
                            + "' trigger='" + trigger + "' line=" + lineNum
                            + " reason=" + r.diagnostics());
                }
            }
            if (!compiled.isEmpty()) {
                result.put(trigger, List.copyOf(compiled));
            }
        }
        return Map.copyOf(result);
    }

    private ActionEngine engine() {
        try {
            EmakiCoreLibPlugin coreLib = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
            return coreLib == null ? null : coreLib.actionEngine();
        } catch (Exception e) {
            return null;
        }
    }
}
