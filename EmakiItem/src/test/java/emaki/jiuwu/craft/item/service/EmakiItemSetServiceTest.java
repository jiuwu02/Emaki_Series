package emaki.jiuwu.craft.item.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemSetLoader;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EquippedSetState;
import emaki.jiuwu.craft.item.model.ItemSetDefinition;
import emaki.jiuwu.craft.item.model.ItemSetMembership;
import emaki.jiuwu.craft.item.model.ItemSetThreshold;
import net.kyori.adventure.text.Component;

class EmakiItemSetServiceTest {

    @Test
    void renderRevertsThresholdThenStaticMigratesLegacyAndAppliesStaticBeforeThreshold() {
        EmakiItemIdentifier identifier = mock(EmakiItemIdentifier.class);
        EmakiItemPdcWriter pdcWriter = mock(EmakiItemPdcWriter.class);
        ItemSetLoreRenderer loreRenderer = mock(ItemSetLoreRenderer.class);
        ItemOperationLedger ledger = mock(ItemOperationLedger.class);
        EmakiItemSetService service = service(identifier, pdcWriter, loreRenderer, ledger);
        MockItem item = mockItem(List.of("base", "", "old-static-a", "old-static-b"));

        ItemSetMembership membership = new ItemSetMembership("alpha", "piece");
        EmakiItemDefinition definition = mock(EmakiItemDefinition.class);
        when(definition.id()).thenReturn("item");
        when(definition.definitionSignature()).thenReturn("definition-signature");
        when(definition.setMembership()).thenReturn(membership);

        ItemSetDefinition setDefinition = mock(ItemSetDefinition.class);
        when(setDefinition.id()).thenReturn("alpha");
        when(setDefinition.displayName()).thenReturn("Alpha");
        when(setDefinition.totalPieces()).thenReturn(2);

        ItemSetThreshold threshold = new ItemSetThreshold(
                1,
                List.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(Map.of("action", "append", "content", List.of("threshold-action"))),
                List.of()
        );
        EquippedSetState state = mock(EquippedSetState.class);
        when(state.definition()).thenReturn(setDefinition);
        when(state.activeCount()).thenReturn(1);
        when(state.equippedPieces()).thenReturn(Set.of("piece"));
        when(state.activeThresholds()).thenReturn(List.of(threshold));
        when(state.mergedNameActions()).thenReturn(List.of());
        when(state.mergedLoreActions()).thenReturn(threshold.loreActions());
        when(state.mergedAttributes()).thenReturn(Map.of());
        when(state.mergedSkills()).thenReturn(List.of());

        when(identifier.setLoreLines(item.stack())).thenReturn(3);
        when(loreRenderer.render(state)).thenReturn(List.of("new-static"));

        service.renderSetItem(item.stack(), definition, membership, state);

        InOrder order = inOrder(ledger, identifier, pdcWriter);
        order.verify(ledger).revert(item.stack(), EmakiItemSetService.thresholdOperationId("alpha"));
        order.verify(ledger).revert(item.stack(), EmakiItemSetService.staticLoreOperationId("alpha"));
        order.verify(identifier).setLoreLines(item.stack());
        order.verify(ledger).apply(
                eq(item.stack()),
                eq(EmakiItemSetService.staticLoreOperationId("alpha")),
                eq("emakiitem:set_display"),
                eq(List.of()),
                any(),
                eq(Map.of())
        );
        order.verify(ledger).apply(
                eq(item.stack()),
                eq(EmakiItemSetService.thresholdOperationId("alpha")),
                eq("emakiitem:set_display"),
                eq(List.of()),
                eq(threshold.loreActions()),
                any()
        );
        order.verify(pdcWriter).writeDynamicSet(
                eq(item.stack()),
                eq(definition),
                eq("alpha"),
                eq("piece"),
                eq(1),
                eq(2),
                eq(List.of(1)),
                eq(0),
                eq(Map.of()),
                eq(List.of()),
                any()
        );

        assertEquals(List.of("base"), item.loreLines());
        ArgumentCaptor<Object> staticActions = ArgumentCaptor.forClass(Object.class);
        verify(ledger).apply(
                eq(item.stack()),
                eq(EmakiItemSetService.staticLoreOperationId("alpha")),
                eq("emakiitem:set_display"),
                eq(List.of()),
                staticActions.capture(),
                eq(Map.of())
        );
        assertEquals(
                List.of(Map.of("action", "append", "content", List.of("", "new-static"))),
                staticActions.getValue()
        );
    }

    @Test
    void clearRevertsBothOperationsBeforeLegacyTailAndDynamicPdcAreCleared() {
        EmakiItemIdentifier identifier = mock(EmakiItemIdentifier.class);
        EmakiItemPdcWriter pdcWriter = mock(EmakiItemPdcWriter.class);
        ItemOperationLedger ledger = mock(ItemOperationLedger.class);
        EmakiItemSetService service = service(identifier, pdcWriter, mock(ItemSetLoreRenderer.class), ledger);
        MockItem item = mockItem(List.of("base", "", "legacy-static"));
        EmakiItemDefinition definition = mock(EmakiItemDefinition.class);
        when(definition.setMembership()).thenReturn(ItemSetMembership.empty());
        when(identifier.setId(item.stack())).thenReturn("legacy-set");
        when(identifier.setLoreLines(item.stack())).thenReturn(2);

        service.clearSetPresentation(item.stack(), definition);

        InOrder order = inOrder(ledger, identifier, pdcWriter);
        order.verify(identifier).setId(item.stack());
        order.verify(ledger).revert(item.stack(), EmakiItemSetService.thresholdOperationId("legacy-set"));
        order.verify(ledger).revert(item.stack(), EmakiItemSetService.staticLoreOperationId("legacy-set"));
        order.verify(identifier).setLoreLines(item.stack());
        order.verify(pdcWriter).clearDynamicSet(item.stack(), definition);
        assertEquals(List.of("base"), item.loreLines());
    }

    @Test
    void staticBlockUsesOneOptionalSeparatorAndLegacyMigrationHasNoLineCap() {
        List<String> base = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            base.add("base-" + index);
        }
        List<String> legacy = new ArrayList<>(base);
        legacy.add("");
        legacy.add("old-static");

        assertEquals(base, EmakiItemSetService.stripPreviousSetLore(legacy, 2));
        assertEquals(List.of("static"), EmakiItemSetService.staticLoreBlock(List.of(), List.of("static")));
        assertEquals(List.of("", "static"), EmakiItemSetService.staticLoreBlock(base, List.of("static")));
        assertEquals(List.of(), EmakiItemSetService.staticLoreBlock(base, List.of()));
    }

    private static EmakiItemSetService service(EmakiItemIdentifier identifier,
            EmakiItemPdcWriter pdcWriter,
            ItemSetLoreRenderer loreRenderer,
            ItemOperationLedger ledger) {
        return new EmakiItemSetService(
                mock(EmakiItemLoader.class),
                mock(EmakiItemSetLoader.class),
                mock(EmakiItemFactory.class),
                identifier,
                pdcWriter,
                loreRenderer,
                () -> null,
                ledger
        );
    }

    private static MockItem mockItem(List<String> initialLore) {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        List<Component> lore = new ArrayList<>();
        initialLore.forEach(line -> lore.add(MiniMessages.parse(line)));

        Material material = mock(Material.class);
        when(material.isAir()).thenReturn(false);
        when(stack.getType()).thenReturn(material);
        when(stack.getItemMeta()).thenReturn(meta);
        when(stack.setItemMeta(any(ItemMeta.class))).thenReturn(true);
        when(meta.hasLore()).thenAnswer(ignored -> !lore.isEmpty());
        when(meta.lore()).thenAnswer(ignored -> lore.isEmpty() ? null : new ArrayList<>(lore));
        org.mockito.Mockito.doAnswer(ignored -> {
            lore.clear();
            return null;
        }).when(meta).lore(org.mockito.ArgumentMatchers.isNull());
        org.mockito.Mockito.doAnswer(invocation -> {
            List<? extends Component> updated = invocation.getArgument(0);
            lore.clear();
            lore.addAll(updated);
            return null;
        }).when(meta).lore(org.mockito.ArgumentMatchers.anyList());
        return new MockItem(stack, meta);
    }

    private record MockItem(ItemStack stack, ItemMeta meta) {

        List<String> loreLines() {
            List<String> lines = ItemTextBridge.loreLines(meta);
            return lines == null ? List.of() : lines;
        }
    }
}
