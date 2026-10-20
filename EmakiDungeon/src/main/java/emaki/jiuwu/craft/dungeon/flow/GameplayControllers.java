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
        java.util.Set<String> candidates = new java.util.LinkedHashSet<>();
        for (FlowModules.OptionCase optionCase : option.cases()) {
            candidates.add(optionCase.caseId());
        }
        var triggerEvent = new emaki.jiuwu.craft.dungeon.api.event.DungeonChoiceEvent(
                instance.instanceId(), instance.dungeonId(), optionId,
                option.selectMode(), candidates);
        Bukkit.getPluginManager().callEvent(triggerEvent);
        if (triggerEvent.isCancelled()) {
            debug.logRaw("dungeon", (UUID) null, "option trigger cancelled | option=" + optionId);
            return;
        }
        promptForPick(runtime, option, player, playerId);
    }

    private void promptForPick(FlowRuntime runtime, FlowModules.OptionDef option, Player player, UUID playerId) {
        String optionId = option.optionId();
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
            if (result.status() == ChatInputResult.Status.TIMEOUT) {
                messages.send(player, "flow.option.expired");
                return;
            }
            if (!result.submitted()) {
                messages.send(player, "flow.option.aborted");
                return;
            }
            String text = Texts.lower(Texts.trim(result.text()));
            List<String> picked = parsePick(text, caseIds);
            boolean multi = "multi".equalsIgnoreCase(option.selectMode());
            if (picked.isEmpty() || (!multi && picked.size() != 1)) {
                messages.send(player, "flow.option.invalid");
                promptForPick(runtime, option, player, playerId);
                return;
            }
            if (multi && !withinSelectBounds(option, picked.size())) {
                messages.send(player, "flow.option.bounds", Map.of(
                        "min", String.valueOf(option.minSelect()),
                        "max", String.valueOf(option.maxSelect())));
                promptForPick(runtime, option, player, playerId);
                return;
            }
            if (!multi) {
                finalizePick(runtime, optionId, picked, playerId);
                return;
            }
            confirmMulti(runtime, option, player, playerId, picked);
        }));
    }

    private void confirmMulti(FlowRuntime runtime, FlowModules.OptionDef option, Player player, UUID playerId,
            List<String> picked) {
        messages.send(player, "flow.option.confirm_prompt", Map.of("options", String.join(", ", picked)));
        long timeout = option.timeLimitSeconds() > 0 ? option.timeLimitSeconds() : 60L;
        chatInput.await(new ChatInputRequest(plugin, player, timeout, List.of("取消", "cancel"), result -> {
            if (result.status() == ChatInputResult.Status.TIMEOUT) {
                finalizePick(runtime, option.optionId(), picked, playerId);
                return;
            }
            if (!result.submitted()) {
                messages.send(player, "flow.option.aborted");
                return;
            }
            String text = Texts.lower(Texts.trim(result.text()));
            if ("确认".equals(text) || "confirm".equals(text) || "yes".equals(text) || "y".equals(text)) {
                finalizePick(runtime, option.optionId(), picked, playerId);
                return;
            }
            messages.send(player, "flow.option.aborted");
        }));
    }

    private boolean withinSelectBounds(FlowModules.OptionDef option, int size) {
        int minimum = Math.max(1, option.minSelect());
        int maximum = Math.max(minimum, option.maxSelect());
        return size >= minimum && size <= maximum;
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
        boolean multi = "multi".equalsIgnoreCase(option.selectMode());
        java.util.Set<String> picked = multi
                ? new java.util.LinkedHashSet<>(pickedCaseIds)
                : java.util.Set.of(pickedCaseIds.get(0));
        runtime.setFact("option_case:" + optionId, multi
                ? String.join(",", pickedCaseIds)
                : pickedCaseIds.get(0));
        debug.logRaw("dungeon", (UUID) null, "option picked | option=" + optionId
                + " | cases=" + String.join(",", pickedCaseIds));
        Bukkit.getPluginManager().callEvent(new emaki.jiuwu.craft.dungeon.api.event.DungeonOptionConfirmEvent(
                instance.instanceId(), instance.dungeonId(), optionId, picked));
        List<FlowModules.OptionCase> sealed = new ArrayList<>();
        for (FlowModules.OptionCase optionCase : option.cases()) {
            if (!picked.contains(optionCase.caseId())) {
                sealed.add(optionCase);
            }
        }
        pullbackSealed(option, sealed);
        for (FlowModules.OptionCase optionCase : sealed) {
            sealRegions(runtime, optionCase);
        }
        if (!sealed.isEmpty()) {
            java.util.Set<String> sealedIds = new java.util.LinkedHashSet<>();
            for (FlowModules.OptionCase optionCase : sealed) {
                sealedIds.add(optionCase.caseId());
            }
            instance.commands().submit(new WorldCommand() {
                @Override
                public int blockCost() {
                    return 0;
                }

                @Override
                public void execute() {
                    Bukkit.getPluginManager().callEvent(
                            new emaki.jiuwu.craft.dungeon.api.event.DungeonSealPathEvent(
                                    instance.instanceId(), instance.dungeonId(), optionId, sealedIds));
                }
            });
        }
        String endingId = resolveEnding(option, pickedCaseIds);
        if (!endingId.isBlank() && endingHandler != null) {
            triggerEnding(runtime, pickedCaseIds.get(0), endingId);
        }
    }

    private String resolveEnding(FlowModules.OptionDef option, List<String> pickedCaseIds) {
        String canonical = canonicalKey(pickedCaseIds);
        for (Map.Entry<String, String> entry : option.endingMap().entrySet()) {
            if (canonicalKey(java.util.Arrays.asList(entry.getKey().split(","))).equals(canonical)) {
                return entry.getValue();
            }
        }
        if (pickedCaseIds.size() == 1) {
            String only = pickedCaseIds.get(0);
            for (FlowModules.OptionCase optionCase : option.cases()) {
                if (only.equals(optionCase.caseId()) && !optionCase.endingId().isBlank()) {
                    return optionCase.endingId();
                }
            }
        }
        return "";
    }

    private static String canonicalKey(java.util.Collection<String> parts) {
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            String trimmed = Texts.trim(part);
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        java.util.Collections.sort(normalized);
        return String.join(",", normalized);
    }

    private void pullbackSealed(FlowModules.OptionDef option, List<FlowModules.OptionCase> sealed) {
        boolean anyRegion = false;
        for (FlowModules.OptionCase optionCase : sealed) {
            if (!optionCase.sealRegions().isEmpty()) {
                anyRegion = true;
                break;
            }
        }
        if (!anyRegion) {
            return;
        }
        World world = Bukkit.getWorld(instance.instanceId());
        FlowPoint safe = option.safePoint() != null ? option.safePoint() : option.point();
        if (world == null || safe == null) {
            return;
        }
        org.bukkit.Location target = new org.bukkit.Location(world,
                safe.x(), safe.y(), safe.z(), safe.yaw(), safe.pitch());
        for (Player member : List.copyOf(world.getPlayers())) {
            org.bukkit.Location location = member.getLocation();
            boolean inside = false;
            for (FlowModules.OptionCase optionCase : sealed) {
                for (FlowRegion region : optionCase.sealRegions()) {
                    if (region.contains(location.getX(), location.getY(), location.getZ())) {
                        inside = true;
                        break;
                    }
                }
                if (inside) {
                    break;
                }
            }
            if (inside) {
                try {
                    member.teleport(target);
                } catch (Throwable ignored) {
                }
            }
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
