package emaki.jiuwu.craft.item.script;

import org.graalvm.polyglot.Value;

record ItemScriptPayload(String id, Value parseFn, Value clearFn, Value applyFn) {
}
