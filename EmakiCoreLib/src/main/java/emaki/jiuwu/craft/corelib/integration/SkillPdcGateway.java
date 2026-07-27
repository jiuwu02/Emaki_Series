package emaki.jiuwu.craft.corelib.integration;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.skills.protocol.EquipmentSkillPdcCodec;
import emaki.jiuwu.craft.skills.protocol.SkillPdcMutation;

@Deprecated(since = "4.5.21", forRemoval = true)
public final class SkillPdcGateway {

    private final DebugLogger debugLogger;

    public SkillPdcGateway() {
        this(null);
    }

    public SkillPdcGateway(DebugLogger debugLogger) {
        this.debugLogger = debugLogger;
    }

    public void write(ItemStack itemStack, Collection<String> skillIds) {
        observe(itemStack, EquipmentSkillPdcCodec.write(itemStack, skillIds));
    }

    public void write(ItemStack itemStack, Collection<String> skillIds, String activeSlot) {
        observe(itemStack, EquipmentSkillPdcCodec.write(itemStack, skillIds, activeSlot, Map.of()));
    }

    public void write(
            ItemStack itemStack,
            Collection<String> skillIds,
            String activeSlot,
            Map<String, String> boundTriggers) {
        observe(itemStack, EquipmentSkillPdcCodec.write(itemStack, skillIds, activeSlot, boundTriggers));
    }

    public void clear(ItemStack itemStack) {
        observe(itemStack, EquipmentSkillPdcCodec.clear(itemStack));
    }

    public List<String> readSkillIds(ItemStack itemStack) {
        return EquipmentSkillPdcCodec.read(itemStack).skillIds();
    }

    public String readActiveSlot(ItemStack itemStack) {
        return EquipmentSkillPdcCodec.read(itemStack).activeSlot();
    }

    public Map<String, String> readBoundTriggers(ItemStack itemStack) {
        return EquipmentSkillPdcCodec.read(itemStack).boundTriggers();
    }

    public void copy(ItemStack original, ItemStack rebuilt) {
        observe(rebuilt, EquipmentSkillPdcCodec.copy(original, rebuilt));
    }

    private void observe(ItemStack itemStack, SkillPdcMutation mutation) {
        if (!isDebugEnabled() || mutation == null) {
            return;
        }
        debugLogger.log("pdc", (java.util.UUID) null, "pdc.skill_payload", Map.of(
                "operation", mutation.operation(),
                "item", itemStack == null ? "null" : itemStack.getType(),
                "amount", itemStack == null ? 0 : itemStack.getAmount(),
                "before", mutation.before().values(),
                "after", mutation.after().values(),
                "committed", mutation.committed(),
                "reason", mutation.reason()
        ));
    }

    private boolean isDebugEnabled() {
        return debugLogger != null && debugLogger.shouldLog("pdc", (java.util.UUID) null);
    }
}
