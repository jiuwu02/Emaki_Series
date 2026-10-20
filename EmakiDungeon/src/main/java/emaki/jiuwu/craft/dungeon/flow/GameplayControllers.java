package emaki.jiuwu.craft.dungeon.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.chat.ChatInputRequest;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputResult;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.WorldCommand;

public final class GameplayControllers implements FlowControllers {

    private final Plugin plugin;
    private final DungeonInstance instance;
    private final MessageService messages;
    private final ChatInputService chatInput;
    private final DebugLogger debug;
    private final GateController gateController;
    private final WaveController waveController;
    private final emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxService rewardBox;

    private volatile Consumer<String> failHandler;
    private volatile BiConsumer<String, Integer> tempItemHandler;
    private volatile Consumer<String> completeHandler;
    private volatile BiConsumer<String, String> endingHandler;

    public GameplayControllers(Plugin plugin,
            DungeonInstance instance,
            MessageService messages,
            ChatInputService chatInput,
            DebugLogger debug,
            GateController gateController,
            WaveController waveController,
            emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxService rewardBox) {
        this.plugin = plugin;
        this.instance = instance;
        this.messages = messages;
        this.chatInput = chatInput;
        this.debug = debug;
        this.gateController = gateController;
        this.waveController = waveController;
        this.rewardBox = rewardBox;
    }

    public void bindHandlers(Consumer<String> failHandler, BiConsumer<String, Integer> tempItemHandler) {
        this.failHandler = failHandler;
        this.tempItemHandler = tempItemHandler;
    }

    public void bindSettlementHandlers(Consumer<String> completeHandler,
            BiConsumer<String, String> endingHandler) {
        this.completeHandler = completeHandler;
        this.endingHandler = endingHandler;
    }

    @Override
    public boolean openGate(FlowRuntime runtime, String gateId, UUID playerId) {
        return gateController.openGate(runtime, gateId, playerId);
    }

    @Override
    public boolean spawnWave(FlowRuntime runtime, String spawnId, int waveIndex, UUID playerId) {
        return waveController.spawnWave(runtime, spawnId, waveIndex);
    }

    @Override
    public void handleRoomEntered(FlowRuntime runtime, String roomId, UUID playerId) {
        debug.logRaw("dungeon", (UUID) null, "room entered | room=" + roomId);
        waveController.notifyRoomEntered(roomId);
    }

    @Override
    public void triggerFail(FlowRuntime runtime, String reason) {
        if (failHandler != null) {
            failHandler.accept(reason);
        }
    }

    @Override
    public void triggerComplete(FlowRuntime runtime, String cause) {
        if (completeHandler != null) {
            completeHandler.accept(cause);
        }
    }

    @Override
    public void triggerEnding(FlowRuntime runtime, String optionCase, String endingId) {
        if (endingHandler != null) {
            endingHandler.accept(optionCase, endingId);
        }
    }

    @Override
    public void giveTempItem(FlowRuntime runtime, String itemId, int amount) {
        if (tempItemHandler != null) {
            tempItemHandler.accept(itemId, amount);
        }
    }

    @Override
    public void handleRewardOpen(FlowRuntime runtime, String rewardId, UUID playerId) {
        FlowModules.RewardDef reward = runtime.definition().rewards().get(rewardId);
        if (reward == null || !reward.head().enabled() || reward.point() == null) {
            return;
        }
        debug.logRaw("dungeon", (UUID) null,
                "chest opened | reward=" + rewardId + " | player=" + playerId);
        String lootTable = reward.lootTable();
        if (lootTable.isBlank()) {
            return;
        }
        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(lootTable);
        if (key == null) {
            return;
        }
        org.bukkit.loot.LootTable table = Bukkit.getLootTable(key);
        if (table == null) {
            debug.logRaw("dungeon", (UUID) null,
                    "loot table missing | reward=" + rewardId + " | table=" + lootTable);
            return;
        }
        java.util.Collection<org.bukkit.inventory.ItemStack> generated =
                table.populateLoot(new java.util.Random(), null);
        if (generated.isEmpty()) {
            return;
        }
        if (playerId == null) {
            return;
        }
        var point = reward.point();
        java.util.List<org.bukkit.inventory.ItemStack> leftover =
                rewardBox.deposit(playerId, new ArrayList<>(generated));
        if (!leftover.isEmpty()) {
            org.bukkit.World world = Bukkit.getWorld(instance.instanceId());
            org.bukkit.Location fallback = world == null ? null : new org.bukkit.Location(world,
                    point.x(), point.y(), point.z(), point.yaw(), point.pitch());
            rewardBox.deliverOrDrop(playerId, leftover, world, fallback);
        }
    }

    @Override
    public void handleOptionTrigger(FlowRuntime runtime, String optionId, UUID playerId) {
        FlowModules.OptionDef option = runtime.definition().options().get(optionId);
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        if (option == null || player == null || option.cases().isEmpty()) {
            return;
        }
        StringBuilder prompt = new StringBuilder();
        List<String> caseIds = new ArrayList<>(option.cases().size());
        for (int i = 0; i < option.cases().size(); i++) {
            FlowModules.OptionCase optionCase = option.cases().get(i);
            caseIds.add(optionCase.caseId());
            prompt.append("<gray>").append(i + 1).append(". </gray><white>")
                    .append(optionCase.label()).append("</white> ");
        }
        messages.send(player, "flow.option.prompt", Map.of("options", prompt.toString()));
        long timeout = option.timeLimitSeconds() > 0 ? option.timeLimitSeconds() : 60L;
        chatInput.await(new ChatInputRequest(plugin, player, timeout, List.of("取消", "cancel"), result -> {
            if (!result.submitted()) {
                messages.send(player, "flow.option.expired");
                return;
            }
            String text = Texts.lower(Texts.trim(result.text()));
            List<String> picked = parsePick(text, caseIds);
            if (picked.isEmpty()) {
                messages.send(player, "flow.option.invalid");
                return;
            }
            finalizePick(runtime, optionId, picked, player.getUniqueId());
        }));
    }

    private List<String> parsePick(String text, List<String> caseIds) {
        List<String> picked = new ArrayList<>();
        for (String token : text.split("[,\\s]+")) {
            if (token.isEmpty()) {
                continue;
            }
            try {
                int index = Integer.parseInt(token);
                if (index >= 1 && index <= caseIds.size()) {
                    picked.add(caseIds.get(index - 1));
                }
            } catch (NumberFormatException exception) {
                for (String caseId : caseIds) {
                    if (caseId.equalsIgnoreCase(token)) {
                        picked.add(caseId);
                    }
                }
            }
        }
        return picked;
    }

    private void finalizePick(FlowRuntime runtime, String optionId, List<String> pickedCaseIds, UUID playerId) {
        FlowModules.OptionDef option = runtime.definition().options().get(optionId);
        if (option == null || pickedCaseIds.isEmpty()) {
            return;
        }
        var choiceEvent = new emaki.jiuwu.craft.dungeon.api.event.DungeonChoiceEvent(
                instance.instanceId(), instance.dungeonId(), optionId,
                option.selectMode(), new java.util.LinkedHashSet<>(pickedCaseIds));
        Bukkit.getPluginManager().callEvent(choiceEvent);
        if (choiceEvent.isCancelled()) {
            debug.logRaw("dungeon", (UUID) null,
                    "option choice cancelled | option=" + optionId);
            return;
        }
        boolean multi = "multi".equalsIgnoreCase(option.selectMode());
        java.util.Set<String> picked = multi
                ? new java.util.HashSet<>(pickedCaseIds)
                : java.util.Set.of(pickedCaseIds.get(0));
        runtime.setFact("option_case:" + optionId, multi
                ? String.join(",", pickedCaseIds)
                : pickedCaseIds.get(0));
        debug.logRaw("dungeon", (UUID) null,
                "option picked | option=" + optionId + " | cases=" + String.join(",", pickedCaseIds));
        for (FlowModules.OptionCase optionCase : option.cases()) {
            if (!picked.contains(optionCase.caseId())) {
                sealRegions(runtime, optionCase);
            }
        }
        String endingId = "";
        for (FlowModules.OptionCase optionCase : option.cases()) {
            if (picked.contains(optionCase.caseId()) && !optionCase.endingId().isBlank()) {
                endingId = optionCase.endingId();
                break;
            }
        }
        if (!endingId.isBlank() && endingHandler != null) {
            triggerEnding(runtime, pickedCaseIds.get(0), endingId);
        }
    }

    private void sealRegions(FlowRuntime runtime, FlowModules.OptionCase optionCase) {
        for (FlowRegion region : optionCase.sealRegions()) {
            instance.commands().submit(new WorldCommand() {
                @Override
                public int blockCost() {
                    return (int) ((Math.floor(Math.max(region.minX(), region.maxX()))
                            - Math.floor(Math.min(region.minX(), region.maxX())) + 1)
                            * (Math.floor(Math.max(region.minY(), region.maxY()))
                            - Math.floor(Math.min(region.minY(), region.maxY())) + 1)
                            * (Math.floor(Math.max(region.minZ(), region.maxZ()))
                            - Math.floor(Math.min(region.minZ(), region.maxZ())) + 1));
                }

                @Override
                public void execute() {
                    World world = Bukkit.getWorld(instance.instanceId());
                    if (world == null) {
                        return;
                    }
                    var barrier = Material.BARRIER.createBlockData();
                    int minX = (int) Math.floor(Math.min(region.minX(), region.maxX()));
                    int maxX = (int) Math.floor(Math.max(region.minX(), region.maxX()));
                    int minY = (int) Math.floor(Math.min(region.minY(), region.maxY()));
                    int maxY = (int) Math.floor(Math.max(region.minY(), region.maxY()));
                    int minZ = (int) Math.floor(Math.min(region.minZ(), region.maxZ()));
                    int maxZ = (int) Math.floor(Math.max(region.minZ(), region.maxZ()));
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                world.getBlockAt(x, y, z).setBlockData(barrier, false);
                            }
                        }
                    }
                }
            });
        }
    }

    @Override
    public void handleMobDeath(FlowRuntime runtime, UUID entityId) {
        waveController.onMobDeath(entityId);
    }

    @Override
    public void syncBossPoints(FlowRuntime runtime) {
        waveController.syncBossPoints(runtime);
    }
}
