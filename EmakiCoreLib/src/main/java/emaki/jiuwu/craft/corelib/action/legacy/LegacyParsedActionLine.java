package emaki.jiuwu.craft.corelib.action.legacy;

import java.util.Map;

/**
 * One parsed old-syntax action line.
 *
 * <p>Moved here from the removed v1 action package so the one-shot converter can still read old syntax.</p>
 *
 * <p>{@code arguments} is unordered, which is why {@code LegacyLineConverter} re-reads argument order from
 * the original text rather than iterating this map: emitting arguments in hash order would rewrite lines
 * that need no rewriting and make the migration diff unreviewable.</p>
 *
 * @param lineNumber one-based line number within its configuration list
 * @param rawLine the original text
 * @param actionId the old action id
 * @param arguments parsed arguments, unordered
 * @param control the control prefixes the line carried
 */
record LegacyParsedActionLine(int lineNumber,
        String rawLine,
        String actionId,
        Map<String, String> arguments,
        LegacyLineControl control) {
}
