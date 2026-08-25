package emaki.jiuwu.craft.corelib.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.cost.CostReceipt.CurrencyRecord;
import emaki.jiuwu.craft.corelib.cost.CostReceipt.FailureReason;
import emaki.jiuwu.craft.corelib.cost.CostReceipt.MaterialRecord;
import emaki.jiuwu.craft.corelib.cost.CostReceipt.RollbackResult;

@DisplayName("扣费凭据的金额守恒约束")
class CostReceiptTest {

    @Test
    @DisplayName("负金额被夹为 0，不会变成倒付给玩家")
    void negativeCurrencyAmountIsClamped() {
        assertEquals(0D, new CurrencyRecord("vault", "coin", -50D).amount());
        assertEquals(12.5D, new CurrencyRecord("vault", "coin", 12.5D).amount());
    }

    @Test
    @DisplayName("负材料数被夹为 0，不会变成倒还给玩家")
    void negativeMaterialAmountIsClamped() {
        assertEquals(0L, new MaterialRecord(List.of("minecraft-iron_ingot"), -8L).amount());
        assertEquals(3L, new MaterialRecord(List.of("minecraft-iron_ingot"), 3L).amount());
    }

    @Test
    @DisplayName("RollbackResult.full() 必须是完整回滚且无残留")
    void fullRollbackIsComplete() {
        RollbackResult full = RollbackResult.full();
        assertTrue(full.complete());
        assertTrue(full.remainingCurrencies().isEmpty());
        assertTrue(full.remainingMaterials().isEmpty());
    }

    @Test
    @DisplayName("failure 不带回滚参数时视为已全额补偿")
    void failureWithoutRollbackIsFullyCompensated() {
        CostReceipt receipt = CostReceipt.failure(FailureReason.INSUFFICIENT_FUNDS);
        assertFalse(receipt.success());
        assertEquals(FailureReason.INSUFFICIENT_FUNDS, receipt.failureReason());
        assertTrue(receipt.compensationComplete());
        assertTrue(receipt.remainingCurrencies().isEmpty());
        assertTrue(receipt.remainingMaterials().isEmpty());
    }

    @Test
    @DisplayName("部分回滚必须如实上报未补偿残留，不得伪装成已补偿")
    void partialRollbackReportsRemainder() {
        RollbackResult partial = new RollbackResult(false,
                List.of(new CurrencyRecord("vault", "coin", 30D)),
                List.of(new MaterialRecord(List.of("minecraft-diamond"), 2L)));

        CostReceipt receipt = CostReceipt.failure(FailureReason.COMPENSATION_FAILED, partial);

        assertFalse(receipt.success());
        assertFalse(receipt.compensationComplete());
        assertEquals(1, receipt.remainingCurrencies().size());
        assertEquals(30D, receipt.remainingCurrencies().getFirst().amount());
        assertEquals(1, receipt.remainingMaterials().size());
        assertEquals(2L, receipt.remainingMaterials().getFirst().amount());
    }

    @Test
    @DisplayName("失败凭据不携带任何已扣记录")
    void failureCarriesNoChargedRecords() {
        CostReceipt receipt = CostReceipt.failure(FailureReason.INSUFFICIENT_MATERIALS);
        assertTrue(receipt.chargedCurrencies().isEmpty());
        assertTrue(receipt.chargedMaterials().isEmpty());
    }

    @Test
    @DisplayName("成功凭据记录已扣项且无残留")
    void successRecordsChargesWithoutRemainder() {
        CostReceipt receipt = CostReceipt.success(
                List.of(new CurrencyRecord("vault", "coin", 100D)),
                List.of(new MaterialRecord(List.of("minecraft-iron_ingot"), 5L)),
                RollbackResult::full);

        assertTrue(receipt.success());
        assertEquals(FailureReason.SUCCESS, receipt.failureReason());
        assertTrue(receipt.compensationComplete());
        assertEquals(100D, receipt.chargedCurrencies().getFirst().amount());
        assertEquals(5L, receipt.chargedMaterials().getFirst().amount());
        assertTrue(receipt.remainingCurrencies().isEmpty());
        assertTrue(receipt.remainingMaterials().isEmpty());
    }

    @Test
    @DisplayName("noop 是成功且无扣费的空凭据")
    void noopChargesNothing() {
        CostReceipt receipt = CostReceipt.noop();
        assertTrue(receipt.success());
        assertTrue(receipt.compensationComplete());
        assertTrue(receipt.chargedCurrencies().isEmpty());
        assertTrue(receipt.chargedMaterials().isEmpty());
        assertTrue(receipt.rollback().complete());
    }

    @Test
    @DisplayName("无延迟回滚器时 rollback() 返回完整回滚而非空指针")
    void rollbackWithoutSupplierIsFull() {
        assertTrue(CostReceipt.failure(FailureReason.ECONOMY_UNAVAILABLE).rollback().complete());
    }

    @Test
    @DisplayName("rollback() 委托给延迟回滚器并透传其结果")
    void rollbackDelegatesToSupplier() {
        RollbackResult partial = new RollbackResult(false,
                List.of(new CurrencyRecord("vault", "coin", 7D)), List.of());

        CostReceipt receipt = CostReceipt.success(List.of(), List.of(), () -> partial);

        RollbackResult result = receipt.rollback();
        assertFalse(result.complete());
        assertEquals(7D, result.remainingCurrencies().getFirst().amount());
    }

    @Test
    @DisplayName("凭据不随构造入参的后续改动而变化")
    void receiptSnapshotsInputLists() {
        List<CurrencyRecord> mutable = new ArrayList<>();
        mutable.add(new CurrencyRecord("vault", "coin", 10D));

        CostReceipt receipt = CostReceipt.success(mutable, List.of(), RollbackResult::full);
        mutable.add(new CurrencyRecord("vault", "gem", 999D));

        assertEquals(1, receipt.chargedCurrencies().size());
        assertEquals(10D, receipt.chargedCurrencies().getFirst().amount());
    }

    @Test
    @DisplayName("凭据暴露的列表不可被调用方篡改")
    void exposedListsAreImmutable() {
        CostReceipt receipt = CostReceipt.success(
                List.of(new CurrencyRecord("vault", "coin", 10D)), List.of(), RollbackResult::full);

        assertThrows(UnsupportedOperationException.class,
                () -> receipt.chargedCurrencies().add(new CurrencyRecord("vault", "gem", 1D)));
    }

    @Test
    @DisplayName("材料令牌列表被快照，不随原列表改动")
    void materialTokensAreSnapshotted() {
        List<String> tokens = new ArrayList<>();
        tokens.add("minecraft-iron_ingot");

        MaterialRecord record = new MaterialRecord(tokens, 1L);
        tokens.add("minecraft-diamond");

        assertEquals(1, record.itemTokens().size());
    }

    @Test
    @DisplayName("null 入参归一化为空列表而非空指针")
    void nullInputsBecomeEmptyLists() {
        assertTrue(new CurrencyRecord(null, null, 1D).provider().isEmpty());
        assertTrue(new MaterialRecord(null, 1L).itemTokens().isEmpty());

        RollbackResult nullLists = new RollbackResult(true, null, null);
        assertTrue(nullLists.remainingCurrencies().isEmpty());
        assertTrue(nullLists.remainingMaterials().isEmpty());
    }
}
