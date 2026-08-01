package emaki.jiuwu.craft.corelib.action.legacy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The old-to-new mapping table for the one-shot syntax migration.
 *
 * <p>Every entry here was confirmed against the actual v2 stage declarations in
 * {@code action/builtin/v2}, not against the design document: the document predates the stage
 * implementations and lists targets such as {@code start_task} that were never built.</p>
 *
 * <p>This whole package is temporary. It is deleted once the migration has run everywhere, which is
 * why the table is a plain static map rather than a registry other code could grow to depend on.</p>
 */
final class LegacyMappings {

    /**
     * Old action id to new stage id, for the ids whose target stage exists.
     *
     * <p>The four ids deliberately absent are {@code loopsync}, {@code loopasync}, {@code cancelloop}
     * and {@code usetemplate}: their v2 counterparts do not exist yet, so a line using them is
     * reported as unmappable instead of being rewritten into something that cannot compile.</p>
     */
    private static final Map<String, String> STAGE_IDS = stageIds();

    /**
     * Old placeholder to new placeholder.
     *
     * <p>An exact-key whitelist, never a prefix match. Decision D2 handed the whole
     * {@code %player_*%} namespace to PlaceholderAPI, so rewriting by prefix would corrupt
     * third-party placeholders such as {@code %player_ping%}.</p>
     */
    private static final Map<String, String> PLACEHOLDERS = placeholders();

    /** Old ids that have no v2 stage, tracked so the scanner can explain the skip precisely. */
    private static final Map<String, String> UNMAPPABLE = unmappable();

    /**
     * Parent keys whose string lists are never action lists.
     *
     * <p>Required by the heuristic: a {@code lore} entry can look exactly like an action line.</p>
     */
    private static final Set<String> BLACKLISTED_KEYS = Set.of(
            "lore", "description", "entries", "aliases", "lore_aliases", "tags", "tab_tags",
            "required_skills", "conflicting_skills", "item_sources", "materials", "currencies",
            "allowed_events", "passive_triggers", "worlds", "biomes", "permissions");

    private LegacyMappings() {
    }

    private static Map<String, String> stageIds() {
        Map<String, String> ids = new LinkedHashMap<>();
        // Messaging. `message` is the Skills-side alias of the same old action.
        ids.put("sendmessage", "send_message");
        ids.put("message", "send_message");
        ids.put("broadcastmessage", "broadcast_message");
        ids.put("sendactionbar", "send_action_bar");
        ids.put("sendtitle", "send_title");
        // Feedback. `sound` and `particle` are the Skills-side aliases.
        ids.put("playsound", "play_sound");
        ids.put("sound", "play_sound");
        ids.put("spawnparticle", "spawn_particle");
        ids.put("particle", "spawn_particle");
        // Potion effects.
        ids.put("givepotioneffect", "give_potion_effect");
        ids.put("clearpotioneffects", "clear_potion_effects");
        ids.put("removepotioneffect", "remove_potion_effect");
        // Entity and world effects whose v2 ids differ only by underscores.
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
        // Already identical in v2; listed so the table doubles as the "known id" set.
        for (String same : List.of("damage", "heal", "feed", "ignite", "extinguish", "teleport",
                "explosion", "projectile")) {
            ids.put(same, same);
        }
        return Map.copyOf(ids);
    }

    private static Map<String, String> placeholders() {
        Map<String, String> values = new LinkedHashMap<>();
        // The seven keys PlaceholderRenderer.putPlayerDefaults owns, and nothing else.
        values.put("%player%", "%caster.name%");
        values.put("%player_name%", "%caster.name%");
        values.put("%player_uuid%", "%caster.uuid%");
        values.put("%player_world%", "%caster.world%");
        values.put("%player_x%", "%caster.x%");
        values.put("%player_y%", "%caster.y%");
        values.put("%player_z%", "%caster.z%");
        // Target-side keys move to the dotted namespace.
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

    /**
     * Looks up the new stage id for an old action id.
     *
     * @param oldId the old action id, already lowercased
     * @return the new stage id, or {@code null} when there is no mapping
     */
    static @Nullable String stageId(@Nullable String oldId) {
        return oldId == null ? null : STAGE_IDS.get(oldId);
    }

    /**
     * Reports why an old action id cannot be converted.
     *
     * @param oldId the old action id, already lowercased
     * @return a reason token, or {@code null} when the id is not a known-unmappable one
     */
    static @Nullable String unmappableReason(@Nullable String oldId) {
        return oldId == null ? null : UNMAPPABLE.get(oldId);
    }

    /**
     * Rewrites the Emaki-owned placeholders in one line.
     *
     * <p>Longest key first so that {@code %player_name%} is never matched by {@code %player%}.</p>
     *
     * @param text the text to rewrite
     * @return the rewritten text
     */
    static @NotNull String rewritePlaceholders(@NotNull String text) {
        String result = text;
        for (Map.Entry<String, String> entry : PLACEHOLDERS.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Reports whether a YAML key never holds action lines.
     *
     * @param key the key to test, may be {@code null}
     * @return whether the key is blacklisted
     */
    static boolean blacklisted(@Nullable String key) {
        return key != null && BLACKLISTED_KEYS.contains(key);
    }
}
