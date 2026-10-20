package emaki.jiuwu.craft.dungeon.script;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import emaki.jiuwu.craft.dungeon.api.event.DungeonChallengeCancelEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonChallengeStartEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonChoiceEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonCompleteEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonEnterEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonEndingEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonExitEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonFailEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonGateOpenEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonOptionConfirmEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonRoomClearEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonSealPathEvent;
import emaki.jiuwu.craft.dungeon.trigger.TriggerType;

public final class TriggerBridge implements Listener {

    private final ScriptRuntimeManager scripts;

    public TriggerBridge(ScriptRuntimeManager scripts) {
        this.scripts = scripts;
    }

    private void fire(TriggerType type, String instanceId, String dungeonId, Map<String, String> context) {
        scripts.fireTrigger(type, instanceId, dungeonId, context);
    }

    private static Map<String, String> ctx(Object... pairs) {
        Map<String, String> context = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            if (pairs[index] == null || pairs[index + 1] == null) {
                continue;
            }
            context.put(String.valueOf(pairs[index]), String.valueOf(pairs[index + 1]));
        }
        return context;
    }

    private static String join(Set<String> values) {
        return values == null ? "" : String.join(",", values);
    }

    @EventHandler
    public void onEnter(DungeonEnterEvent event) {
        fire(TriggerType.onEnter, event.getInstanceId(), event.getDungeonId(),
                ctx("player", event.getPlayer(), "partyId", event.getPartyId()));
    }

    @EventHandler
    public void onChallengeStart(DungeonChallengeStartEvent event) {
        fire(TriggerType.onChallengeStart, null, event.getDungeonId(),
                ctx("difficulty", event.getDifficulty(),
                        "dropMode", event.getDropMode(),
                        "windowSeconds", event.getWindowSeconds()));
    }

    @EventHandler
    public void onChallengeCancel(DungeonChallengeCancelEvent event) {
        fire(TriggerType.onChallengeCancel, null, event.getDungeonId(),
                ctx("reason", event.getReason()));
    }

    @EventHandler
    public void onRoomClear(DungeonRoomClearEvent event) {
        fire(TriggerType.onRoomClear, event.getInstanceId(), event.getDungeonId(),
                ctx("roomId", event.getRoomId()));
    }

    @EventHandler
    public void onGateOpen(DungeonGateOpenEvent event) {
        fire(TriggerType.onGateOpen, event.getInstanceId(), event.getDungeonId(),
                ctx("gateId", event.getGateId(), "condition", event.getCondition()));
    }

    @EventHandler
    public void onComplete(DungeonCompleteEvent event) {
        fire(TriggerType.onComplete, event.getInstanceId(), event.getDungeonId(),
                ctx("score", event.getScore(), "durationMs", event.getDurationMs()));
    }

    @EventHandler
    public void onFail(DungeonFailEvent event) {
        fire(TriggerType.onFail, event.getInstanceId(), event.getDungeonId(),
                ctx("reason", event.getReason() == null ? null : event.getReason().name(),
                        "detail", event.getDetail()));
    }

    @EventHandler
    public void onExit(DungeonExitEvent event) {
        fire(TriggerType.onExit, event.getInstanceId(), event.getDungeonId(),
                ctx("player", event.getPlayer(), "reason", event.getReason()));
    }

    @EventHandler
    public void onOptionTrigger(DungeonChoiceEvent event) {
        fire(TriggerType.onOptionTrigger, event.getInstanceId(), event.getDungeonId(),
                ctx("optionId", event.getOptionId(),
                        "selectMode", event.getSelectMode(),
                        "selected", join(event.getSelected())));
    }

    @EventHandler
    public void onOptionConfirm(DungeonOptionConfirmEvent event) {
        fire(TriggerType.onOptionConfirm, event.getInstanceId(), event.getDungeonId(),
                ctx("optionId", event.getOptionId(), "selected", join(event.getSelected())));
    }

    @EventHandler
    public void onSealPath(DungeonSealPathEvent event) {
        fire(TriggerType.onSealPath, event.getInstanceId(), event.getDungeonId(),
                ctx("optionId", event.getOptionId(), "sealed", join(event.getSealedCases())));
    }

    @EventHandler
    public void onEnding(DungeonEndingEvent event) {
        fire(TriggerType.onEnding, event.getInstanceId(), event.getDungeonId(),
                ctx("endingId", event.getEndingId(), "optionCase", event.getOptionCase()));
    }
}
