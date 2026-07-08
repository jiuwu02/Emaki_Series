package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class SetBlockAction extends BaseAction {

    public SetBlockAction() {
        super(
                "setblock",
                "world",
                "Set a vanilla block or Bukkit block data at a resolved location.",
                ActionParameter.optional("material", ActionParameterType.STRING, "", "Block material"),
                ActionParameter.optional("block", ActionParameterType.STRING, "", "Block material alias"),
                ActionParameter.optional("block_data", ActionParameterType.STRING, "", "Bukkit block data"),
                ActionParameter.optional("apply_physics", ActionParameterType.BOOLEAN, "true", "Apply physics"),
                ActionParameter.optional("world", ActionParameterType.STRING, "", "World"),
                ActionParameter.optional("x", ActionParameterType.STRING, "", "X"),
                ActionParameter.optional("y", ActionParameterType.STRING, "", "Y"),
                ActionParameter.optional("z", ActionParameterType.STRING, "", "Z")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        if ((context == null || context.player() == null) && (Texts.isBlank(arguments.get("x")) || Texts.isBlank(arguments.get("y")) || Texts.isBlank(arguments.get("z")))) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "setblock requires x, y and z when no player context is available.");
        }
        BlockData blockData = resolveBlockData(arguments);
        if (blockData == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid setblock material or block_data.");
        }
        if (blockData.getMaterial().isAir() || !blockData.getMaterial().isBlock()) {
            return ActionResult.skipped("setblock material must be a non-air block.");
        }
        ActionLocationResolver.ResolvedLocation resolved = ActionLocationResolver.resolve(context, arguments, id());
        if (!resolved.success()) {
            return resolved.error();
        }
        Location location = resolved.location();
        World world = location.getWorld();
        if (world == null) {
            return ActionResult.failure(ActionErrorType.WORLD_NOT_FOUND, "Unknown world for setblock action.");
        }
        Block block = location.getBlock();
        Material before = block.getType();
        boolean applyPhysics = !Boolean.FALSE.equals(ActionParsers.parseBoolean(arguments.get("apply_physics")));
        block.setBlockData(blockData, applyPhysics);
        return ActionResult.ok(Map.of(
                "world", world.getName(),
                "x", block.getX(),
                "y", block.getY(),
                "z", block.getZ(),
                "before", before.name().toLowerCase(java.util.Locale.ROOT),
                "after", block.getType().name().toLowerCase(java.util.Locale.ROOT)
        ));
    }

    private BlockData resolveBlockData(Map<String, String> arguments) {
        String rawBlockData = stringArg(arguments, "block_data");
        if (Texts.isNotBlank(rawBlockData)) {
            try {
                return Bukkit.createBlockData(rawBlockData);
            } catch (IllegalArgumentException _) {
                return null;
            }
        }
        String rawMaterial = stringArg(arguments, "material");
        if (Texts.isBlank(rawMaterial)) {
            rawMaterial = stringArg(arguments, "block");
        }
        Material material = resolveMaterial(rawMaterial);
        return material == null ? null : material.createBlockData();
    }

    private Material resolveMaterial(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        Material material = ItemSourceUtil.resolveVanillaMaterial(raw);
        if (material != null) {
            return material;
        }
        String trimmed = Texts.trim(raw);
        material = Material.matchMaterial(trimmed);
        if (material != null) {
            return material;
        }
        if (trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("minecraft:")) {
            return Material.matchMaterial(trimmed.substring("minecraft:".length()));
        }
        return null;
    }
}
