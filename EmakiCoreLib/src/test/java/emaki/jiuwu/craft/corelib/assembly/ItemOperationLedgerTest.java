package emaki.jiuwu.craft.corelib.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import net.kyori.adventure.text.Component;

class ItemOperationLedgerTest {

    private static final List<String> BASE_LORE = List.of(
            "duplicate",
            "",
            "anchor",
            "duplicate",
            "omega"
    );

    @ParameterizedTest(name = "{0}")
    @MethodSource("positionCases")
    void sameOperationIdApplyReplaceRevertRestoresBaseLore(String action,
            String anchor,
            List<String> firstContent,
            List<String> replacementContent,
            List<String> expectedFirst,
            List<String> expectedReplacement) {
        MockItem item = mockItem(BASE_LORE);
        ItemOperationLedger ledger = new ItemOperationLedger();

        assertTrue(ledger.apply(item.stack(), "matrix", "test", List.of(),
                List.of(operation(action, anchor, firstContent)), Map.of()));
        assertEquals(expectedFirst, item.loreLines());

        assertTrue(ledger.apply(item.stack(), "matrix", "test", List.of(),
                List.of(operation(action, anchor, replacementContent)), Map.of()));
        assertEquals(expectedReplacement, item.loreLines());

        assertTrue(ledger.revert(item.stack(), "matrix"));
        assertEquals(BASE_LORE, item.loreLines());
    }

    @Test
    void emptyBaseAndBlankRenderedLineRoundTripExactly() {
        MockItem item = mockItem(List.of());
        ItemOperationLedger ledger = new ItemOperationLedger();

        assertTrue(ledger.apply(item.stack(), "blank", "test", List.of(),
                List.of(operation("append", "", List.of("", "visible"))), Map.of()));
        assertEquals(List.of("", "visible"), item.loreLines());

        assertTrue(ledger.revert(item.stack(), "blank"));
        assertEquals(List.of(), item.loreLines());
    }

    @Test
    void revertingEarlierOperationRefreshesLaterRollbackState() {
        MockItem item = mockItem(BASE_LORE);
        ItemOperationLedger ledger = new ItemOperationLedger();

        assertTrue(ledger.apply(item.stack(), "first", "one", List.of(),
                List.of(operation("append", "", List.of("first"))), Map.of()));
        assertTrue(ledger.apply(item.stack(), "second", "two", List.of(),
                List.of(operation("append", "", List.of("second"))), Map.of()));

        assertTrue(ledger.revert(item.stack(), "first"));
        assertEquals(withInserted(BASE_LORE, BASE_LORE.size(), List.of("second")), item.loreLines());

        assertTrue(ledger.revert(item.stack(), "second"));
        assertEquals(BASE_LORE, item.loreLines());
    }

    @Test
    void searchInsertCanBeReplacedThreeHundredTimesWithoutTruncatingBaseLore() {
        List<String> base = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            base.add(index == 150 ? "anchor" : "base-" + index);
        }
        MockItem item = mockItem(base);
        ItemOperationLedger ledger = new ItemOperationLedger();

        for (int round = 0; round < 300; round++) {
            assertTrue(ledger.apply(item.stack(), "search-300", "test", List.of(),
                    List.of(operation("search_insert", "anchor", List.of("", "round-" + round))), Map.of()));
            assertEquals(302, item.loreLines().size());
            assertEquals("anchor", item.loreLines().get(150));
            assertEquals("", item.loreLines().get(151));
            assertEquals("round-" + round, item.loreLines().get(152));
        }

        assertTrue(ledger.revert(item.stack(), "search-300"));
        assertEquals(base, item.loreLines());
    }

    private static Stream<Arguments> positionCases() {
        List<String> first = List.of("", "duplicate");
        List<String> replacement = List.of("replacement-a", "replacement-b");
        int anchorIndex = BASE_LORE.indexOf("anchor");
        return Stream.of(
                Arguments.of("append", "", first, replacement,
                        withInserted(BASE_LORE, BASE_LORE.size(), first),
                        withInserted(BASE_LORE, BASE_LORE.size(), replacement)),
                Arguments.of("prepend", "", first, replacement,
                        withInserted(BASE_LORE, 0, first),
                        withInserted(BASE_LORE, 0, replacement)),
                Arguments.of("insert_below", "anchor", first, replacement,
                        withInserted(BASE_LORE, anchorIndex + 1, first),
                        withInserted(BASE_LORE, anchorIndex + 1, replacement)),
                Arguments.of("insert_above", "anchor", first, replacement,
                        withInserted(BASE_LORE, anchorIndex, first),
                        withInserted(BASE_LORE, anchorIndex, replacement)),
                Arguments.of("search_insert", "anchor", first, replacement,
                        withInserted(BASE_LORE, anchorIndex + 1, first),
                        withInserted(BASE_LORE, anchorIndex + 1, replacement)),
                Arguments.of("search_insert_below", "anchor", first, replacement,
                        withInserted(BASE_LORE, anchorIndex + 1, first),
                        withInserted(BASE_LORE, anchorIndex + 1, replacement)),
                Arguments.of("search_insert_above", "anchor", first, replacement,
                        withInserted(BASE_LORE, anchorIndex, first),
                        withInserted(BASE_LORE, anchorIndex, replacement))
        );
    }

    private static Map<String, Object> operation(String action, String anchor, List<String> content) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("action", action);
        if (anchor != null && !anchor.isBlank()) {
            operation.put("anchor", anchor);
        }
        operation.put("content", content);
        return operation;
    }

    private static List<String> withInserted(List<String> base, int index, List<String> inserted) {
        List<String> result = new ArrayList<>(base);
        result.addAll(index, inserted);
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static MockItem mockItem(List<String> initialLore) {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        List<Component> lore = new ArrayList<>();
        if (initialLore != null) {
            initialLore.forEach(line -> lore.add(MiniMessages.parse(line)));
        }
        Map<NamespacedKey, Object> pdc = new LinkedHashMap<>();

        Material material = mock(Material.class);
        when(material.isAir()).thenReturn(false);
        when(stack.getType()).thenReturn(material);
        when(stack.getItemMeta()).thenReturn(meta);
        when(stack.setItemMeta(any(ItemMeta.class))).thenReturn(true);
        when(meta.getPersistentDataContainer()).thenReturn(container);
        when(meta.hasLore()).thenAnswer(ignored -> !lore.isEmpty());
        when(meta.lore()).thenAnswer(ignored -> lore.isEmpty() ? null : new ArrayList<>(lore));
        doAnswer(ignored -> {
            lore.clear();
            return null;
        }).when(meta).lore(isNull());
        doAnswer(invocation -> {
            List<? extends Component> updated = invocation.getArgument(0);
            lore.clear();
            lore.addAll(updated);
            return null;
        }).when(meta).lore(anyList());

        doAnswer(invocation -> {
            pdc.put(invocation.getArgument(0), invocation.getArgument(2));
            return null;
        }).when(container).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
        when(container.has(any(NamespacedKey.class), any(PersistentDataType.class)))
                .thenAnswer(invocation -> pdc.containsKey(invocation.getArgument(0)));
        when(container.get(any(NamespacedKey.class), any(PersistentDataType.class)))
                .thenAnswer(invocation -> pdc.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            pdc.remove(invocation.getArgument(0));
            return null;
        }).when(container).remove(any(NamespacedKey.class));

        return new MockItem(stack, meta);
    }

    private record MockItem(ItemStack stack, ItemMeta meta) {

        List<String> loreLines() {
            List<String> lines = ItemTextBridge.loreLines(meta);
            return lines == null ? List.of() : lines;
        }
    }
}
