package emaki.jiuwu.craft.item.model;

import java.util.List;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;

public record ItemConditions(ConditionBlock block) {

    public ItemConditions {
        block = block == null ? ConditionBlock.empty() : block;
    }

    public static ItemConditions empty() {
        return new ItemConditions(ConditionBlock.empty());
    }

    public boolean configured() {
        return block.configured();
    }

    public ConditionGroup group() {
        return block.group();
    }

    public String denyMessage() {
        return block.failMessage();
    }

    public List<String> passActions() {
        return block.passActions();
    }

    public List<String> failActions() {
        return block.failActions();
    }
}
