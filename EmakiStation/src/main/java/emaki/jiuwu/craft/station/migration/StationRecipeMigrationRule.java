package emaki.jiuwu.craft.station.migration;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyContentMigrator;
import emaki.jiuwu.craft.corelib.legacy.LegacyEntryMover;
import emaki.jiuwu.craft.corelib.legacy.YamlBlockLocator;

/**
 * 站点配方旧键自动迁移规则.
 *
 * <p>只改真正被新写法取代的键:
 * <ul>
 *   <li>材料列表 {@code materials[]} 内的 {@code item_source} 改写成 {@code item_sources} 列表;</li>
 *   <li>分解配方根级的 {@code input_source} 改写成 {@code item_sources} 列表.</li>
 * </ul>
 *
 * <p>旧键是单个物品源 token, 新键是列表, 因此这里除了改名还要把标量值包成一项列表, 否则加载器
 * 读不到来源. 已经写了新键的配方一律不动, 交给加载器按冲突规则告警.
 *
 * <p>{@code result.outputs[]} 的规范键本来就是单数的 {@code item_source}, 语义相反, 因此不动.
 */
public final class StationRecipeMigrationRule implements LegacyContentMigrator.Rule {

    private static final String MATERIALS = "materials";
    private static final String LEGACY_ITEM_SOURCE = "item_source";
    private static final String ITEM_SOURCES = "item_sources";
    private static final String LEGACY_INPUT_SOURCE = "input_source";

    @Override
    public @NotNull LegacyContentMigrator.Outcome apply(@NotNull List<String> lines) {
        List<String> working = new ArrayList<>(lines);
        List<String> changes = new ArrayList<>();
        migrateMaterials(working, changes);
        migrateInputSource(working, changes);
        if (changes.isEmpty()) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        return new LegacyContentMigrator.Outcome(working, changes);
    }

    private void migrateMaterials(List<String> working, List<String> changes) {
        int[] materials = LegacyEntryMover.findRootBlock(working, MATERIALS);
        if (materials == null) {
            return;
        }
        boolean renamed = false;
        // 从后往前改, 避免插入列表项后影响尚未处理的行号.
        for (int index = materials[1] - 1; index > materials[0]; index--) {
            if (convertEntry(working, index, LEGACY_ITEM_SOURCE)) {
                renamed = true;
            }
        }
        if (renamed) {
            changes.add(MATERIALS + "." + LEGACY_ITEM_SOURCE);
        }
    }

    private void migrateInputSource(List<String> working, List<String> changes) {
        int[] legacy = LegacyEntryMover.findRootBlock(working, LEGACY_INPUT_SOURCE);
        if (legacy == null || LegacyEntryMover.findRootBlock(working, ITEM_SOURCES) != null) {
            return;
        }
        if (convertEntry(working, legacy[0], LEGACY_INPUT_SOURCE)) {
            changes.add(LEGACY_INPUT_SOURCE);
        }
    }

    /**
     * 把单行 {@code item_source: "token"} 改写成 {@code item_sources:} + 一项列表.
     *
     * @param legacyKey 要改写的旧键名
     * @return 是否发生了改写
     */
    private static boolean convertEntry(List<String> working, int index, String legacyKey) {
        String line = working.get(index);
        int keyColumn = keyColumnOf(line);
        if (keyColumn < 0) {
            return false;
        }
        String rest = line.substring(keyColumn);
        String prefix = legacyKey + ":";
        if (!rest.startsWith(prefix)) {
            return false;
        }
        String value = rest.substring(prefix.length()).trim();
        if (value.isEmpty() || value.startsWith("#")) {
            // 旧键写成块状结构时语义不明确, 留给加载器告警, 不做猜测式改写.
            return false;
        }
        String pad = " ".repeat(keyColumn);
        working.set(index, pad + ITEM_SOURCES + ":");
        working.add(index + 1, pad + "  - " + value);
        return true;
    }

    /** 返回键名所在列; 支持 {@code key:} 与 {@code - key:} 两种行首形状. */
    private static int keyColumnOf(String line) {
        int indent = YamlBlockLocator.indentOf(line);
        if (indent == Integer.MAX_VALUE) {
            return -1;
        }
        int column = indent;
        if (line.startsWith("- ", column)) {
            column += 2;
            while (column < line.length() && line.charAt(column) == ' ') {
                column++;
            }
        }
        return column;
    }
}
