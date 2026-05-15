import { textValue } from '../lib/miniMessage';
import { asStringList } from '../lib/itemUtils';

/** Structured editor for Name/Lore action lists. */
export type ActionEntry = { type: string; params: Record<string, unknown> };

export function ActionsEditor({ actions, onChange, actionTypes, mode }: { actions: ActionEntry[]; onChange: (actions: ActionEntry[]) => void; actionTypes: string[]; mode: 'name' | 'lore' }) {
  const update = (i: number, patch: Partial<ActionEntry>) => {
    const next = [...actions];
    next[i] = { ...next[i], ...patch };
    onChange(next);
  };
  const updateParam = (i: number, key: string, value: unknown) => {
    const next = [...actions];
    next[i] = { ...next[i], params: { ...next[i].params, [key]: value } };
    onChange(next);
  };
  const remove = (i: number) => onChange(actions.filter((_, idx) => idx !== i));
  const add = () => onChange([...actions, { type: actionTypes[0] ?? '', params: {} }]);
  const moveUp = (i: number) => { if (i <= 0) return; const next = [...actions]; [next[i - 1], next[i]] = [next[i], next[i - 1]]; onChange(next); };
  const moveDown = (i: number) => { if (i >= actions.length - 1) return; const next = [...actions]; [next[i], next[i + 1]] = [next[i + 1], next[i]]; onChange(next); };

  return (
    <div className="prop-actions">
      {actions.map((action, i) => (
        <div className="prop-action-item" key={i}>
          <div className="prop-action-head">
            <span className="prop-action-grip">≡</span>
            <select value={action.type} onChange={e => update(i, { type: e.target.value, params: {} })} aria-label={`动作类型 ${i + 1}`}>
              {actionTypes.map(t => <option key={t} value={t}>{t}</option>)}
              {!actionTypes.includes(action.type) && action.type && <option value={action.type}>{action.type}</option>}
            </select>
            <span className="prop-action-controls">
              <button onClick={() => moveUp(i)} disabled={i === 0} aria-label="上移">↑</button>
              <button onClick={() => moveDown(i)} disabled={i === actions.length - 1} aria-label="下移">↓</button>
              <button className="prop-action-del" onClick={() => remove(i)} aria-label="删除">×</button>
            </span>
          </div>
          <div className="prop-action-params">
            {mode === 'name' && (
              <>
                <input type="text" value={textValue(action.params.value)} onChange={e => updateParam(i, 'value', e.target.value)} placeholder="value" aria-label="value" />
                {action.type === 'regex_replace' && (
                  <>
                    <input type="text" value={textValue(action.params.regex_pattern)} onChange={e => updateParam(i, 'regex_pattern', e.target.value)} placeholder="regex_pattern" aria-label="regex_pattern" />
                    <input type="text" value={textValue(action.params.replacement)} onChange={e => updateParam(i, 'replacement', e.target.value)} placeholder="replacement" aria-label="replacement" />
                  </>
                )}
              </>
            )}
            {mode === 'lore' && (
              <>
                <textarea rows={2} value={asStringList(action.params.content).join('\n')} onChange={e => updateParam(i, 'content', e.target.value.split('\n'))} placeholder="content (每行一个)" aria-label="content" />
                {(action.type.includes('search') || action.type.includes('insert') || action.type.includes('anchor')) && (
                  <>
                    <input type="text" value={textValue(action.params.target_pattern)} onChange={e => updateParam(i, 'target_pattern', e.target.value)} placeholder="target_pattern" aria-label="target_pattern" />
                    <input type="text" value={textValue(action.params.anchor)} onChange={e => updateParam(i, 'anchor', e.target.value)} placeholder="anchor" aria-label="anchor" />
                  </>
                )}
              </>
            )}
          </div>
        </div>
      ))}
      <button className="prop-add" onClick={add}>+ 添加动作</button>
    </div>
  );
}

/** Parse raw name_actions from data map. */
export function parseNameActions(data: Record<string, unknown>): ActionEntry[] {
  const list = Array.isArray(data.name_actions) ? data.name_actions : [];
  return list.map(raw => {
    const r = (raw && typeof raw === 'object' && !Array.isArray(raw)) ? raw as Record<string, unknown> : {};
    return { type: textValue(r.type), params: { value: r.value, regex_pattern: r.regex_pattern, replacement: r.replacement } };
  });
}

/** Parse raw lore_actions from data map. */
export function parseLoreActions(data: Record<string, unknown>): ActionEntry[] {
  const list = Array.isArray(data.lore_actions) ? data.lore_actions : [];
  return list.map(raw => {
    const r = (raw && typeof raw === 'object' && !Array.isArray(raw)) ? raw as Record<string, unknown> : {};
    return { type: textValue(r.type), params: { content: r.content, target_pattern: r.target_pattern, anchor: r.anchor } };
  });
}

/** Serialize action entries back to raw format. */
export function serializeActions(actions: ActionEntry[]): unknown[] {
  return actions.map(a => {
    const entry: Record<string, unknown> = { type: a.type };
    for (const [k, v] of Object.entries(a.params)) {
      if (v !== undefined && v !== '' && !(Array.isArray(v) && v.length === 0)) entry[k] = v;
    }
    return entry;
  });
}
