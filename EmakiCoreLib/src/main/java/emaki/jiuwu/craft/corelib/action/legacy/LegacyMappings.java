package emaki.jiuwu.craft.corelib.action.legacy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class LegacyMappings {

    private static final Map<String, String> STAGE_IDS = stageIds();

    private static final Map<String, String> PLACEHOLDERS = placeholders();

    private static final Map<String, String> UNMAPPABLE = unmappable();

    private static final Set<String> BLACKLISTED_KEYS = Set.of(
            "lore", "description", "entries", "aliases", "lore_aliases", "tags", "tab_tags",
            "required_skills", "conflicting_skills", "item_sources", "materials", "currencies",
            "allowed_events", "passive_triggers", "worlds", "biomes", "permissions");

    private LegacyMappings() {
    }

    private static Map<String, String> stageIds() {
        Map<String, String> ids = new LinkedHashMap<>();

        ids.put("sendmessage", "send_message");
        ids.put("message", "send_message");
        ids.put("broadcastmessage", "broadcast_message");
        ids.put("sendactionbar", "send_action_bar");
        ids.put("sendtitle", "send_title");

        ids.put("playsound", "play_sound");
        ids.put("sound", "play_sound");
        ids.put("spawnparticle", "spawn_particle");
        ids.put("particle", "spawn_particle");

        ids.put("givepotioneffect", "give_potion_effect");
        ids.put("clearpotioneffects", "clear_potion_effects");
        ids.put("removepotioneffect", "remove_potion_effect");

        ids.put("killentity", "kill_entity");
        ids.put("spawnentity", "spawn_entity");
        ids.put("placeblock", "place_block");
        ids.put("setblock", "set_block");
        ids.put("breakblock", "break_block");
        ids.put("runcommandasconsole", "run_command_as_console");
        ids.put("runcommandasplayer", "run_command_as_player");
        ids.put("runcommandasop", "run_command_as_op");
        ids.put("giveitem", "give_item");
        ids.put("takeitem", "take_item");
        ids.put("setitem", "set_item");
        ids.put("clearitem", "clear_item");
        ids.put("dropitem", "drop_item");
        ids.put("repairitem", "repair_item");
        ids.put("damageitem", "damage_item");
        ids.put("givemoney", "give_money");
        ids.put("takemoney", "take_money");
        ids.put("setmoney", "set_money");
        ids.put("giveexp", "give_exp");
        ids.put("takeexp", "take_exp");
        ids.put("setexp", "set_exp");
        ids.put("sethealth", "set_health");
        ids.put("bossbarshow", "boss_bar_show");
        ids.put("bossbarhide", "boss_bar_hide");
        ids.put("castmythicskill", "cast_mythic_skill");

        for (String same : List.of("damage", "heal", "feed", "ignite", "extinguish", "teleport",
                "explosion", "projectile")) {
            ids.put(same, same);
        }
        return Map.copyOf(ids);
    }

    private static Map<String, String> placeholders() {
        Map<String, String> values = new LinkedHashMap<>();

        values.put("%player%", "%caster.name%");
        values.put("%player_name%", "%caster.name%");
        values.put("%player_uuid%", "%caster.uuid%");
        values.put("%player_world%", "%caster.world%");
        values.put("%player_x%", "%caster.x%");
        values.put("%player_y%", "%caster.y%");
        values.put("%player_z%", "%caster.z%");

        values.put("%target_name%", "%target.name%");
        values.put("%target_uuid%", "%target.uuid%");
        values.put("%target_world%", "%target.world%");
        values.put("%target_x%", "%target.x%");
        values.put("%target_y%", "%target.y%");
        values.put("%target_z%", "%target.z%");
        values.put("%has_target%", "%target.present%");
        return Map.copyOf(values);
    }

    private static Map<String, String> unmappable() {
        Map<String, String> reasons = new LinkedHashMap<>();
        reasons.put("loopsync", "no_start_task_stage");
        reasons.put("loopasync", "no_start_task_stage");
        reasons.put("cancelloop", "no_stop_task_stage");
        reasons.put("usetemplate", "no_run_sequence_source");
        return Map.copyOf(reasons);
    }

    static @Nullable String stageId(@Nullable String oldId) {
        return oldId == null ? null : STAGE_IDS.get(oldId);
    }

    static @Nullable String unmappableReason(@Nullable String oldId) {
        return oldId == null ? null : UNMAPPABLE.get(oldId);
    }

    static @NotNull String rewritePlaceholders(@NotNull String text) {
        String result = text;
        for (Map.Entry<String, String> entry : PLACEHOLDERS.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    static boolean blacklisted(@Nullable String key) {
        return key != null && BLACKLISTED_KEYS.contains(key);
    }
}
