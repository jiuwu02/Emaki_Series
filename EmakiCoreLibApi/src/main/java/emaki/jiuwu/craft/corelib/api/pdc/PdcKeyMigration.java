package emaki.jiuwu.craft.corelib.api.pdc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 把历史的带点 PDC 键迁移到扁平键。
 *
 * <p>历史键用 {@code '.'} 连接分区与字段（{@code emaki_attribute:item.attributes.source.x.payload}）。
 * Bukkit 的 {@code YamlConfiguration} 把 {@code '.'} 当路径分隔符，这类键无法在 YAML 里表达，
 * 第三方插件无法手写。{@code PdcPartition.SEPARATOR} 改成 {@code '_'} 后新键可直接写进 YAML，
 * 但已落盘的物品/玩家数据仍是老键，需要迁移。
 *
 * <p>放在 Api 模块而非 CoreLib：{@code EmakiSkillsApi} / {@code EmakiItemApi} 也要用它做
 * 懒转换，而 Api 模块只依赖 {@code emaki-corelib-api}。第三方若自行写过 PDC 键，
 * 也能用这里的规则表判断自己的键是否受影响。
 *
 * <p>两条使用路径：
 * <ul>
 *   <li><b>懒转换</b>：各模块读取入口调 {@link #readWithMigration}，按单个键定向迁移，O(1)。
 *       物品读取在战斗中每次伤害计算都会发生，不能在这里做全键扫描。</li>
 *   <li><b>命令扫描</b>：{@link #migrateAll} 遍历容器内所有键，用于
 *       {@code /emakicorelib pdc-convert}，一次性批量处理。</li>
 * </ul>
 *
 * <p><b>线程</b>：所有方法都要在持有该 PDC 的对象所属线程调用（物品通常是主线程）。
 */
public final class PdcKeyMigration {

    /** 迁移规则的匹配方式。 */
    private enum RuleKind {
        /** 键内所有 {@code '.'} 都是分段连接符，全部替换。 */
        FULL,
        /**
         * 只替换前缀内的 {@code '.'}，保留其余部分。
         *
         * <p>用于 {@code emaki:item_state.*}：字段名由服主在物品 YAML 里定义，
         * {@code ItemStateConfig.normalizeKey()} 不过滤点号，所以服主可能写出
         * {@code my.field}。那个点不是连接符，替换会让 {@code my.field} 与
         * {@code my_field} 撞成同一个键。
         */
        PREFIX,
        /**
         * 只替换后缀前的那一个 {@code '.'}，保留其余部分。
         *
         * <p>用于装配的层快照键 {@code emaki:<层命名空间>.snapshot}：层命名空间由
         * 各插件在运行时注册，静态规则表无法枚举，只能反过来按后缀匹配。
         * 只改连接符，因此即便命名空间本身含点（{@code my.ns.snapshot}
         * → {@code my.ns_snapshot}）结果依然正确。
         */
        SUFFIX
    }

    /**
     * 一条迁移规则。
     *
     * @param namespace     命名空间（不变，只有 key 部分改名）
     * @param legacyPrefix  历史键前缀，含点
     * @param kind          匹配方式
     */
    private record Rule(String namespace, String legacyPrefix, RuleKind kind) {

        Rule {
            namespace = Objects.requireNonNullElse(namespace, "").toLowerCase(Locale.ROOT);
            legacyPrefix = Objects.requireNonNullElse(legacyPrefix, "").toLowerCase(Locale.ROOT);
        }

        boolean matches(String keyNamespace, String legacyPath) {
            if (!namespace.equals(keyNamespace)) {
                return false;
            }
            // SUFFIX 规则的 legacyPrefix 字段存的是后缀，匹配方向相反。
            return kind == RuleKind.SUFFIX
                    ? legacyPath.endsWith(legacyPrefix) && legacyPath.length() > legacyPrefix.length()
                    : legacyPath.startsWith(legacyPrefix);
        }
    }

    /**
     * 规则表。顺序有意义：更具体的前缀必须排在更宽的前缀之前，
     * {@link #newKeyPath} 取第一条命中的规则。
     */
    private static final List<Rule> RULES = List.of(
            // --- emaki:item_state（PREFIX：服主自定义字段名可能含点，不能全替换）---
            // meta.* 是内置的三个键，连接符点要替换
            new Rule("emaki", "item_state.meta.", RuleKind.PREFIX),
            // 其余是服主定义的字段，只替换 item_state 后的那一个连接符点
            new Rule("emaki", "item_state.", RuleKind.PREFIX),

            // --- emaki_attribute ---
            new Rule("emaki_attribute", "item.attributes.", RuleKind.FULL),
            new Rule("emaki_attribute", "combat.", RuleKind.FULL),
            new Rule("emaki_attribute", "item.", RuleKind.FULL),
            new Rule("emaki_attribute", "projectile.", RuleKind.FULL),

            // --- emaki_skills ---
            new Rule("emaki_skills", "item.skills.", RuleKind.FULL),

            // --- emaki（CoreLib assembly / Item 套装 / Gem）---
            new Rule("emaki", "item.", RuleKind.FULL),
            new Rule("emaki", "emakiitem.", RuleKind.FULL),
            new Rule("emaki", "gem.", RuleKind.FULL),
            // 装配层快照挂在根分区上，键形如 <层命名空间>.snapshot。
            // 必须排在上面几条 FULL 之后：万一出现 item.foo.snapshot，应由 FULL 整体处理。
            new Rule("emaki", ".snapshot", RuleKind.SUFFIX),

            // --- emakiforge ---
            new Rule("emakiforge", "forge.", RuleKind.FULL),

            // --- emaki_strengthen ---
            new Rule("emaki_strengthen", "strengthen.", RuleKind.FULL),

            // --- emakilevel（只写镜像，迁移只为清掉老键，避免 PAPI 读到两份）---
            new Rule("emakilevel", "player.", RuleKind.FULL)
    );

    /**
     * 扫描时的类型探测顺序。
     *
     * <p>{@code item_state} 的服主自定义字段类型由配置决定（{@code ItemStateType}
     * 映射到 INTEGER/LONG/DOUBLE/STRING/**BYTE**），扫描时拿不到配置，只能探测。
     * Bukkit 的 {@code has(key, type)} 按 NBT tag 类型精确判断，不同类型不会互相误命中。
     * BYTE 放最后：它是 BOOLEAN 的底层类型，最容易被漏掉。
     */
    private static final List<PersistentDataType<?, ?>> PROBE_TYPES = List.of(
            PersistentDataType.STRING,
            PersistentDataType.DOUBLE,
            PersistentDataType.LONG,
            PersistentDataType.INTEGER,
            PersistentDataType.BYTE
    );

    private PdcKeyMigration() {
    }

    /**
     * {@return 历史键路径对应的新键路径，无规则命中时返回 {@code null}}
     *
     * @param namespace  命名空间
     * @param legacyPath 历史键的 key 部分（不含命名空间）
     */
    public static @Nullable String newKeyPath(@Nullable String namespace, @Nullable String legacyPath) {
        if (namespace == null || legacyPath == null || legacyPath.indexOf('.') < 0) {
            // 不含点的键本来就是扁平的，不需要迁移。
            return null;
        }
        String ns = namespace.toLowerCase(Locale.ROOT);
        String path = legacyPath.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (!rule.matches(ns, path)) {
                continue;
            }
            String migrated = switch (rule.kind()) {
                case FULL -> path.replace('.', '_');
                case PREFIX -> rule.legacyPrefix().replace('.', '_')
                        + path.substring(rule.legacyPrefix().length());
                case SUFFIX -> path.substring(0, path.length() - rule.legacyPrefix().length())
                        + rule.legacyPrefix().replace('.', '_');
            };
            return migrated.equals(path) ? null : migrated;
        }
        return null;
    }

    /** {@return 历史键对应的新键，无规则命中时返回 {@code null}} */
    public static @Nullable NamespacedKey newKey(@Nullable NamespacedKey legacyKey) {
        if (legacyKey == null) {
            return null;
        }
        String migrated = newKeyPath(legacyKey.getNamespace(), legacyKey.getKey());
        return migrated == null ? null : NamespacedKey.fromString(legacyKey.getNamespace() + ":" + migrated);
    }

    /**
     * 构造历史键：调用方显式提供历史分区路径与字段名。
     *
     * <p><b>为什么不能从新键反推</b>：新键的 {@code '_'} 无法区分哪些原本是连接符
     * {@code '.'}、哪些原本就是字段名里的下划线。
     * {@code item_attributes_source_index} 既可能来自
     * {@code item.attributes.source_index}（真实情况，{@code source_index} 是一个字段名），
     * 也可能来自 {@code item.attributes.source.index}。
     * 动态段更糟：sourceId 经 {@code Texts.normalizeId} 把空格转成下划线，
     * {@code source_my_plugin_payload} 无法判断是 {@code source.my_plugin.payload}
     * 还是 {@code source.my.plugin.payload}。
     *
     * <p>因此懒转换必须由调用方提供历史路径——它本来就知道自己改名前用的是什么。
     * 反方向（老键 → 新键）是单向确定的，{@link #migrateAll} 用那个方向。
     *
     * @param namespace         命名空间
     * @param legacyPartition   历史分区路径，含点，如 {@code "item.attributes"}
     * @param field             字段名，如 {@code "source_index"}；可为空表示分区自身
     * @return 历史键，参数非法时返回 {@code null}
     */
    public static @Nullable NamespacedKey legacyKey(@Nullable String namespace,
            @Nullable String legacyPartition,
            @Nullable String field) {
        if (namespace == null || namespace.isBlank() || legacyPartition == null || legacyPartition.isBlank()) {
            return null;
        }
        String ns = namespace.toLowerCase(Locale.ROOT);
        String path = legacyPartition.toLowerCase(Locale.ROOT);
        if (field != null && !field.isBlank()) {
            path = path + "." + field.toLowerCase(Locale.ROOT);
        }
        return NamespacedKey.fromString(ns + ":" + path);
    }

    /**
     * 定向读取：优先新键，新键缺失时读老键并就地迁移。
     *
     * <p>O(1)，适合放在热路径（战斗中每次伤害计算都会读物品 PDC）。
     *
     * <p><b>注意</b>：调用方拿到 {@code ItemMeta} 后若发生了迁移，必须自己
     * {@code setItemMeta} 回写；本方法只改传入的 container。
     *
     * @param container 目标 PDC
     * @param newKey    新键
     * @param legacyKey 老键，为 {@code null} 时只读新键
     * @param type      值类型
     * @return 读到的值，两个键都没有时返回 {@code null}
     */
    public static <P, C> @Nullable C readWithMigration(@Nullable PersistentDataContainer container,
            @Nullable NamespacedKey newKey,
            @Nullable NamespacedKey legacyKey,
            @Nullable PersistentDataType<P, C> type) {
        if (container == null || newKey == null || type == null) {
            return null;
        }
        if (container.has(newKey, type)) {
            return container.get(newKey, type);
        }
        if (legacyKey == null || !container.has(legacyKey, type)) {
            return null;
        }
        C value = container.get(legacyKey, type);
        if (value == null) {
            return null;
        }
        container.set(newKey, type, value);
        container.remove(legacyKey);
        return value;
    }

    /**
     * 全量扫描：把容器里所有命中规则的老键搬到新键。
     *
     * <p>用于 {@code /emakicorelib pdc-convert}，以及 {@code item_state} 这类
     * 字段名由服主定义、调用方无法枚举历史键的场景。
     *
     * <p>幂等：已经是新键的不动，重复执行返回 0。
     *
     * @param container 目标 PDC
     * @param dryRun    为 {@code true} 时只统计不改写
     * @return 迁移的键数量
     */
    public static int migrateAll(@Nullable PersistentDataContainer container, boolean dryRun) {
        if (container == null) {
            return 0;
        }
        // 先收集再改写：不能在遍历 getKeys() 的同时改容器。
        List<NamespacedKey> legacyKeys = new ArrayList<>();
        for (NamespacedKey key : container.getKeys()) {
            if (newKeyPath(key.getNamespace(), key.getKey()) != null) {
                legacyKeys.add(key);
            }
        }
        if (legacyKeys.isEmpty()) {
            return 0;
        }
        int migrated = 0;
        for (NamespacedKey legacyKey : legacyKeys) {
            NamespacedKey target = newKey(legacyKey);
            if (target == null) {
                continue;
            }
            // 新键已存在时绝不覆盖：那是当前代码刚写入的值，而老键是过期残留。
            // 反向覆盖会把玩家刚获得的属性/等级改回旧值。
            if (hasAnyType(container, target)) {
                if (!dryRun) {
                    container.remove(legacyKey);
                }
                continue;
            }
            if (dryRun) {
                migrated++;
                continue;
            }
            if (copyAnyType(container, legacyKey, target)) {
                container.remove(legacyKey);
                migrated++;
            }
        }
        return migrated;
    }

    /**
     * 删除容器内所有命中规则的历史带点键，<b>不</b>搬运值。
     *
     * <p>写入路径专用：刚写完新键后调用，清掉老键残留。
     * 不能在这里用 {@link #migrateAll}——它会把过期的老键值覆盖到刚写好的新键上。
     *
     * @return 删除的键数量
     */
    public static int purgeLegacyKeys(@Nullable PersistentDataContainer container) {
        if (container == null) {
            return 0;
        }
        List<NamespacedKey> legacyKeys = new ArrayList<>();
        for (NamespacedKey key : container.getKeys()) {
            if (newKeyPath(key.getNamespace(), key.getKey()) != null) {
                legacyKeys.add(key);
            }
        }
        for (NamespacedKey legacyKey : legacyKeys) {
            container.remove(legacyKey);
        }
        return legacyKeys.size();
    }

    /** {@return 该键以任一已知类型存在} */
    private static boolean hasAnyType(PersistentDataContainer container, NamespacedKey key) {
        for (PersistentDataType<?, ?> type : PROBE_TYPES) {
            if (container.has(key, type)) {
                return true;
            }
        }
        return false;
    }

    /** {@return 是否成功搬运；未命中任何已知类型时返回 {@code false} 且不删除源键} */
    private static boolean copyAnyType(PersistentDataContainer container,
            NamespacedKey from,
            NamespacedKey to) {
        for (PersistentDataType<?, ?> type : PROBE_TYPES) {
            if (copyTyped(container, from, to, type)) {
                return true;
            }
        }
        return false;
    }

    private static <P, C> boolean copyTyped(PersistentDataContainer container,
            NamespacedKey from,
            NamespacedKey to,
            PersistentDataType<P, C> type) {
        if (!container.has(from, type)) {
            return false;
        }
        C value = container.get(from, type);
        if (value == null) {
            return false;
        }
        container.set(to, type, value);
        return true;
    }

    /**
     * {@return 容器内待迁移的老键数量}
     *
     * <p>只统计不改写，供 {@code --dry-run} 与诊断使用。
     */
    public static int countLegacyKeys(@Nullable PersistentDataHolder holder) {
        return holder == null ? 0 : migrateAll(holder.getPersistentDataContainer(), true);
    }

    /** {@return 规则表覆盖的命名空间集合，供诊断与测试使用} */
    public static @NotNull Set<String> coveredNamespaces() {
        Set<String> namespaces = new LinkedHashSet<>();
        for (Rule rule : RULES) {
            namespaces.add(rule.namespace());
        }
        return Set.copyOf(namespaces);
    }
}
