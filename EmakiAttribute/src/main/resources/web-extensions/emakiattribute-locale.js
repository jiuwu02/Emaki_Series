(function(e,t){var n=`.ea-diagnostics {
  --text-primary: var(--text);
  --text-secondary: var(--muted);
  --border-subtle: var(--line);
  --surface-card: var(--surface);
  --surface-input: var(--input);
  --surface-muted: var(--surface-2);
  --surface-raised: var(--surface);
  display: grid;
  gap: 14px;
  padding: 14px 18px 28px;
  color: var(--text);
}

.ea-diagnostics__grid {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.ea-card {
  border: 1px solid var(--line);
  background: var(--surface);
  border-radius: 6px;
  padding: 12px;
}

.ea-card h3 {
  margin: 0 0 10px;
  color: var(--text);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: -.01em;
}

.ea-form {
  display: grid;
  gap: 10px;
}

.ea-form label {
  display: grid;
  grid-template-columns: minmax(92px, .75fr) minmax(0, 1.25fr);
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
}

.ea-form input {
  width: 100%;
  min-height: var(--config-field-control-height, 30px);
  border-radius: 6px;
  border: 1px solid var(--line);
  background: var(--input);
  color: var(--text);
  padding: 5px 8px;
  outline: none;
  font: inherit;
  font-size: 12px;
}

.ea-form input:focus {
  border-color: var(--line-2);
}

.ea-form input:focus-visible {
  outline: 1px solid var(--accent);
  outline-offset: 2px;
}

.ea-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ea-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 12px;
}

.ea-table th,
.ea-table td {
  border-bottom: 1px solid var(--line);
  padding: 8px 8px;
  text-align: left;
  vertical-align: top;
}

.ea-table th {
  color: var(--muted);
  font-weight: 700;
}

.ea-table tr:last-child th,
.ea-table tr:last-child td {
  border-bottom: 0;
}

.ea-muted {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.45;
}

.ea-error {
  color: var(--red);
}

.ea-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 2px 7px;
  background: var(--surface-2);
  border: 1px solid var(--line);
  color: var(--muted);
  font-size: 11px;
}

.ea-trace {
  display: grid;
  gap: 10px;
}

.ea-trace__item {
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 10px;
  background: var(--surface);
}

.ea-trace__item strong {
  color: var(--text);
  font-weight: 700;
}

.ea-trace__stages {
  margin-top: 8px;
  display: grid;
  gap: 4px;
  font-size: 12px;
}

pre.ea-json {
  max-height: 300px;
  overflow: auto;
  background: var(--input);
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 8px;
  color: var(--text);
  font-family: ui-monospace, "Cascadia Code", monospace;
  font-size: 11px;
  line-height: 1.45;
  scrollbar-width: thin;
}

@media (max-width: 900px) {
  .ea-diagnostics__grid {
    grid-template-columns: 1fr;
  }

  .ea-actions .ui-button {
    min-height: 44px;
  }
}
`;function r(){(0,e.injectExtensionStyles)(`emakiattribute-diagnostics`,n)}function i({api:n,setToolbar:r,setOutline:i}){let[c,l]=(0,t.useState)(``),[u,d]=(0,t.useState)(``),[f,p]=(0,t.useState)(null),[m,h]=(0,t.useState)([]),[g,_]=(0,t.useState)(``),[v,y]=(0,t.useState)(``),[b,x]=(0,t.useState)(!1);(0,t.useEffect)(()=>(r?.({title:(0,e.t)(`emakiattribute.diagnostics.toolbar.title`),subtitle:(0,e.t)(`emakiattribute.diagnostics.toolbar.subtitle`),dirty:!1,sourceEditable:!1}),i?.({title:(0,e.t)(`emakiattribute.diagnostics.outline.title`),subtitle:`Attribute`,items:[],emptyText:(0,e.t)(`emakiattribute.diagnostics.outline.empty`)}),()=>{r?.(null),i?.(null)}),[r,i]);let S=(0,t.useMemo)(()=>f?.snapshot?.values??{},[f]);async function C(){if(!c.trim()){y((0,e.t)(`emakiattribute.diagnostics.error.playerRequired`));return}x(!0),y(``);try{let e=await n.pluginApi(`attribute`,`source-trace`,{player:c.trim(),attributeId:u.trim()});if(!e?.ok)throw Error(a(e?.error,e?.player));p(e.report),_(JSON.stringify(e.report,null,2))}catch(e){y(e instanceof Error?e.message:String(e))}finally{x(!1)}}async function w(t=`list`){if(!c.trim()){y((0,e.t)(`emakiattribute.diagnostics.error.playerRequired`));return}x(!0),y(``);try{let e=await n.pluginApi(`attribute`,`damage-trace`,{player:c.trim(),action:t});if(!e?.ok)throw Error(a(e?.error,e?.player));t===`clear`?(h([]),_(JSON.stringify(e,null,2))):(h(Array.isArray(e.records)?e.records:[]),_(JSON.stringify(e.last&&Object.keys(e.last).length?e.last:e,null,2)))}catch(e){y(e instanceof Error?e.message:String(e))}finally{x(!1)}}return React.createElement(`div`,{className:`ea-diagnostics`},React.createElement(`div`,{className:`ea-card ea-form`},React.createElement(`h3`,null,(0,e.t)(`emakiattribute.diagnostics.filters.title`)),React.createElement(`label`,null,(0,e.t)(`emakiattribute.diagnostics.filters.player`),React.createElement(`input`,{value:c,onChange:e=>l(e.target.value),placeholder:`Steve`})),React.createElement(`label`,null,(0,e.t)(`emakiattribute.diagnostics.filters.attributeId`),React.createElement(`input`,{value:u,onChange:e=>d(e.target.value),placeholder:`physical_attack`})),React.createElement(`div`,{className:`ea-actions`},React.createElement(e.Button,{size:`sm`,variant:`primary`,disabled:b,onClick:C},(0,e.t)(`emakiattribute.diagnostics.action.loadSources`)),React.createElement(e.Button,{size:`sm`,disabled:b,onClick:()=>w(`list`)},(0,e.t)(`emakiattribute.diagnostics.action.refreshTraces`)),React.createElement(e.Button,{size:`sm`,variant:`danger`,disabled:b,onClick:()=>w(`clear`)},(0,e.t)(`emakiattribute.diagnostics.action.clearTraces`))),React.createElement(`p`,{className:`ea-muted`},(0,e.t)(`emakiattribute.diagnostics.traceHint`)),v?React.createElement(`p`,{className:`ea-muted ea-error`},v):null),React.createElement(`div`,{className:`ea-diagnostics__grid`},React.createElement(`section`,{className:`ea-card`},React.createElement(`h3`,null,(0,e.t)(`emakiattribute.diagnostics.finalAttributes.title`)),Object.keys(S).length===0?React.createElement(`p`,{className:`ea-muted`},(0,e.t)(`emakiattribute.diagnostics.finalAttributes.empty`)):React.createElement(`table`,{className:`ea-table`},React.createElement(`tbody`,null,Object.entries(S).filter(([e])=>!u.trim()||e===u.trim()).map(([e,t])=>React.createElement(`tr`,{key:e},React.createElement(`th`,null,e),React.createElement(`td`,null,o(t))))))),React.createElement(`section`,{className:`ea-card`},React.createElement(`h3`,null,(0,e.t)(`emakiattribute.diagnostics.contributions.title`)),f?.contributions?.length?React.createElement(`table`,{className:`ea-table`},React.createElement(`thead`,null,React.createElement(`tr`,null,React.createElement(`th`,null,(0,e.t)(`emakiattribute.diagnostics.column.attribute`)),React.createElement(`th`,null,(0,e.t)(`emakiattribute.diagnostics.column.value`)),React.createElement(`th`,null,(0,e.t)(`emakiattribute.diagnostics.column.source`)),React.createElement(`th`,null,(0,e.t)(`emakiattribute.diagnostics.column.slot`)),React.createElement(`th`,null,(0,e.t)(`emakiattribute.diagnostics.column.status`)))),React.createElement(`tbody`,null,f.contributions.map((t,n)=>React.createElement(`tr`,{key:`${t.attributeId}-${n}`},React.createElement(`td`,null,t.attributeId),React.createElement(`td`,null,s(t.value)),React.createElement(`td`,null,React.createElement(`span`,{className:`ea-badge`},t.sourceType),` `,t.sourceLabel||t.sourceId),React.createElement(`td`,null,t.slot||`-`),React.createElement(`td`,null,t.conditionPassed===!1?(0,e.t)(`emakiattribute.diagnostics.status.inactive`):(0,e.t)(`emakiattribute.diagnostics.status.active`)))))):React.createElement(`p`,{className:`ea-muted`},(0,e.t)(`emakiattribute.diagnostics.contributions.empty`)))),React.createElement(`section`,{className:`ea-card ea-trace`},React.createElement(`h3`,null,(0,e.t)(`emakiattribute.diagnostics.traces.title`)),m.length===0?React.createElement(`p`,{className:`ea-muted`},(0,e.t)(`emakiattribute.diagnostics.traces.empty`)):m.map(t=>React.createElement(`article`,{className:`ea-trace__item`,key:t.traceId},React.createElement(`strong`,null,`#`,t.traceId,` `,t.attackerLabel,` → `,t.targetLabel),React.createElement(`div`,{className:`ea-muted`},t.damageTypeId,` / `,t.cause,` / final=`,o(t.finalDamage),` / mode=`,t.applyMode),React.createElement(`div`,{className:`ea-trace__stages`},t.stages?.map(n=>React.createElement(`div`,{key:`${t.traceId}-${n.stageId}`},(0,e.t)(`emakiattribute.diagnostics.stage`),` `,React.createElement(`b`,null,n.stageId),`: `,o(n.input),` → `,o(n.output))))))),React.createElement(`section`,{className:`ea-card`},React.createElement(`h3`,null,(0,e.t)(`emakiattribute.diagnostics.json.title`)),React.createElement(`pre`,{className:`ea-json`},g||(0,e.t)(`emakiattribute.diagnostics.json.empty`))))}function a(t,n){if(t===`player_not_found`){let t=typeof n==`string`&&n.trim()?n:``;return t?(0,e.t)(`emakiattribute.diagnostics.error.playerNotFound`,{player:t}):(0,e.t)(`emakiattribute.diagnostics.error.playerNotFoundUnknown`)}return typeof t==`string`&&t.trim()?t:(0,e.t)(`emakiattribute.diagnostics.error.queryFailed`)}function o(e){let t=Number(e??0);return Number.isFinite(t)?t.toFixed(2).replace(/\.00$/,``):`0`}function s(e){let t=Number(e??0);return`${t>=0?`+`:``}${o(t)}`}var c=`EmakiAttribute`,l=e.localeText,u=[[`language`,`语言`,`语言文件 ID，对应 lang/<language>.yml。`,`text`],[`version`,`配置版本`,`默认配置结构版本，通常不建议手动修改。`,`text`],[`hard_lock_damage`,`接管原版伤害`,`开启后未命中白名单的原版伤害也会进入 EmakiAttribute 结算；关闭后只有白名单原因进入。`,`boolean`],[`default_damage_type`,`默认伤害类型`,`未指定伤害类型或环境伤害回退时使用的 damage_types ID。`,`text`],[`vanilla_event_damage`,`原版事件伤害接管`,`原版伤害进入 EA 结算的接管模式与默认伤害类型。`,`object`],[`vanilla_event_damage.enabled`,`启用完美接管`,`开启=完美接管：不取消原版事件，仅把伤害数值替换为 EA 结算值；关闭=兼容模式：取消事件后由 EA 合成重应用。`,`boolean`],[`vanilla_event_damage.damage_type`,`伤害类型`,`这些未单独映射的原版伤害统一使用的 damage_types ID。`,`text`],[`regen_interval_ticks`,`回复间隔`,`生命、法力等资源自然回复的间隔，单位 tick。`,`number`],[`sync_delay_ticks`,`同步延迟`,`属性计算后同步到 Bukkit 原生属性的延迟，单位 tick。`,`number`],[`default_profile`,`默认档案`,`玩家默认资源上限、初始属性基础值和新玩家档案模板。`,`object`],[`default_profile.resources`,`默认资源`,`生命、法力等资源的默认最大值、边界与 Bukkit 同步策略。`,`object`,{creatableChildren:!0}],[`default_profile.attributes`,`默认属性`,`玩家默认拥有的属性基础值，key 为属性 ID。`,`dynamic_map`,{creatableChildren:!0}],[`synthetic_hit_feedback`,`击中反馈`,`仅兼容模式生效：合成重应用伤害后是否补发击退和受伤音效。`,`object`],[`synthetic_hit_feedback.knockback`,`补发击退`,`仅兼容模式生效：合成伤害后是否补发击退。`,`boolean`],[`synthetic_hit_feedback.knockback_strength`,`击退强度`,`仅兼容模式生效：补发击退力度系数。`,`number`],[`synthetic_hit_feedback.hurt_sound`,`受伤音效`,`仅兼容模式生效：合成伤害后是否补发受伤音效。`,`boolean`],[`scaling_curves`,`衰减曲线`,`属性超过阈值后按曲线衰减，防止数值无限膨胀。`,`object`,{creatableChildren:!0}],[`allowed_damage_causes`,`伤害来源白名单`,`允许进入 EmakiAttribute 结算的 Bukkit DamageCause 列表。`,`objectList`]],d={display_name:[`显示名称`,`资源、属性或 GUI 中展示给玩家看的名称。`,`text`],default_max:[`默认最大值`,`资源默认最大值。`,`number`],min_max:[`最大值下限`,`资源最大值允许的最低值。`,`number`],max_max:[`最大值上限`,`资源最大值允许的最高值。`,`number`],sync_to_bukkit:[`同步 Bukkit`,`是否把该资源同步到 Bukkit 原生属性。`,`boolean`],full_on_init:[`初始满值`,`初始化档案时是否把资源填充至最大值。`,`boolean`],default_value:[`默认值`,`属性或资源的默认基础数值。`,`number`],group:[`分组`,`属性所属功能分组，例如 physical、spell、utility。`,`text`],role:[`角色`,`属性定位，例如 offense、defense、sustain。`,`text`],summary:[`摘要`,`属性或权重条目的短说明。`,`text`],id:[`ID`,`定义文件的唯一标识。`,`text`],value_kind:[`数值类型`,`属性数值语义，例如固定值、百分比、概率、回复或资源。`,`enum`],target_type:[`目标类型`,`属性作用目标，例如通用、原版属性、资源或伤害。`,`enum`],target_id:[`目标 ID`,`target_type 为 VANILLA、RESOURCE 或 DAMAGE 时映射的目标 ID。`,`text`],min_value:[`最小值`,`属性允许的最小值，低于此值会被钳制。`,`number`],max_value:[`最大值`,`属性允许的最大值，超过此值会被钳制。`,`number`],allow_negative:[`允许负值`,`是否允许该属性为负数。`,`boolean`],priority:[`优先级`,`词条读取或匹配优先级，数值越大越先尝试。`,`number`],lore_format_id:[`词条格式`,`关联 lore_formats 下的格式 ID。`,`text`],lore_patterns:[`词条正则`,`从物品 Lore 中识别属性数值的正则表达式列表。`,`list`],attribute_power:[`属性战力`,`该属性每 1 点对应的战力评分系数。`,`number`],tags:[`标签`,`属性标签列表（如 DEBUFF），供 MythicMobs 按标签批量增删临时属性。`,`list`],temporary_stack_mode:[`临时叠加模式`,`作为临时属性重复施加时的叠加模式：REPLACE 覆盖，STACK 相加。`,`enum`],child_bonuses:[`子属性加成`,`父级属性按子属性 ID 提供的加成映射，可新增任意子属性键。`,`dynamic_map`],description:[`说明`,`定义文件的详细说明，用于文档和调试输出。`,`text`],aliases:[`别名`,`伤害类型可被引用的别名列表。`,`list`],allowed_events:[`允许事件`,`允许触发该伤害类型的 Bukkit DamageCause 列表。`,`list`],hard_lock:[`硬锁定`,`是否强制接管该伤害类型对应的原版事件。`,`boolean`],stages:[`结算阶段`,`伤害结算的有序阶段列表。`,`list`],recovery:[`恢复规则`,`造成伤害后的吸血或资源恢复规则。`,`object`],attacker_message:[`攻击者消息`,`伤害结算后发送给攻击者的消息模板。`,`text`],target_message:[`受击者消息`,`伤害结算后发送给受击者的消息模板。`,`text`],kind:[`阶段类型`,`伤害阶段计算类型。`,`enum`],source:[`来源`,`阶段或恢复数据来源。`,`enum`],resistance_source:[`抗性来源`,`恢复抗性属性的来源。`,`enum`],mode:[`计算模式`,`阶段对输入伤害的计算模式。`,`enum`],flat_attributes:[`固定属性`,`参与固定值计算的属性 ID 列表。`,`stringList`],percent_attributes:[`百分比属性`,`参与百分比计算的属性 ID 列表。`,`stringList`],chance_attributes:[`概率属性`,`决定阶段是否触发的概率属性 ID 列表。`,`stringList`],multiplier_attributes:[`倍率属性`,`触发后的倍率属性 ID 列表。`,`stringList`],resistance_attributes:[`抗性属性`,`参与恢复抗性计算的属性 ID 列表。`,`stringList`],expression:[`计算表达式`,`自定义计算表达式，支持 {input}、{flat}、{percent} 等变量。`,`text`],min_result:[`结果下限`,`阶段或恢复结果允许的最小值。`,`number`],max_result:[`结果上限`,`阶段或恢复结果允许的最大值。`,`number`],min_chance:[`概率下限`,`阶段触发概率的最小值。`,`number`],max_chance:[`概率上限`,`阶段触发概率的最大值。`,`number`],min_multiplier:[`倍率下限`,`阶段倍率允许的最小值。`,`number`],max_multiplier:[`倍率上限`,`阶段倍率允许的最大值。`,`number`],format:[`格式模板`,`词条显示格式，支持 {name}、{value}、{sign} 等占位符。`,`text`],precision:[`精度`,`数值显示的小数位数。`,`number`],read_priority:[`读取优先级`,`从 Lore 解析属性时的匹配优先级。`,`number`],read_patterns:[`读取正则`,`从 Lore 文本中提取数值的正则表达式列表。`,`list`],source_id:[`来源 ID`,`条件规则来源标识，用于日志和调试追踪。`,`text`],type:[`条件逻辑`,`多条件组合逻辑。`,`enum`],invalid_as_failure:[`解析失败视为失败`,`条件表达式解析失败时是否视为不通过。`,`boolean`],required_count:[`需要满足数量`,`at_least / exactly 条件逻辑下需要满足的最少条件数量。`,`number`],schema_version:[`结构版本`,`条件规则结构版本。`,`number`],entries:[`条件列表`,`具体条件项列表，每项包含 type、key/pattern 和 condition 表达式。`,`list`],key:[`PDC 键名`,`要匹配的 PDC 数据键名。`,`text`],pattern:[`正则模式`,`用于从 Lore 中提取数值的正则表达式。`,`text`],condition:[`判定表达式`,`支持 {value}、{player_level}、{player_name} 等变量的判定表达式。`,`text`],require_match:[`必须匹配`,`是否要求正则必须命中才视为通过。`,`boolean`]},f=[[`schema_version`,`结构版本`,`属性权重配置结构版本。`,`text`],[`groups`,`分组`,`属性分组说明。`,`object`,{creatableChildren:!0}],[`roles`,`角色`,`属性角色说明。`,`object`,{creatableChildren:!0}],[`attributes`,`属性权重`,`各属性的分组、角色、摘要和战力评分系数。`,`object`,{creatableChildren:!0}],[`scores`,`属性分数`,`属性 ID 到战力分数的映射，可新增任意属性键。`,`dynamic_map`,{creatableChildren:!0,createTemplates:[{id:`attribute-score`,label:l(`属性分数`,`Attribute score`),fields:[{path:`value`,label:`分数`,comment:`该属性的战力评分系数。`,type:`number`,defaultValue:1}]}]}]],p=[[`id`,`ID`,`属性定义唯一标识。`,`text`],[`display_name`,`显示名称`,`属性展示名称。`,`text`],[`description`,`说明`,`属性说明文本。`,`text`],[`group`,`分组`,`属性所属功能分组。`,`text`],[`role`,`角色`,`属性定位。`,`text`],[`summary`,`摘要`,`属性短说明。`,`text`],[`value_kind`,`数值类型`,`属性数值语义。`,`enum`,{options:[`FLAT`,`PERCENT`,`CHANCE`,`REGEN`,`RESOURCE`],optionLabelPrefix:`value_kind`}],[`target_type`,`目标类型`,`属性作用目标类型。`,`enum`,{options:[`GENERIC`,`VANILLA`,`RESOURCE`,`DAMAGE`],optionLabelPrefix:`target_type`}],[`target_id`,`目标 ID`,`target_type 为 VANILLA、RESOURCE 或 DAMAGE 时映射的目标 ID。`,`text`],[`default_value`,`默认值`,`属性默认基础值。`,`number`],[`min_value`,`最小值`,`属性允许的最小值。`,`number`],[`max_value`,`最大值`,`属性允许的最大值。`,`number`],[`allow_negative`,`允许负值`,`是否允许该属性为负数。`,`boolean`],[`priority`,`优先级`,`词条读取或匹配优先级。`,`number`],[`lore_format_id`,`词条格式`,`关联 lore_formats 下的格式 ID。`,`text`],[`lore_patterns`,`词条正则`,`从物品 Lore 中识别属性数值的正则表达式列表。`,`stringList`],[`attribute_power`,`属性战力`,`该属性每 1 点对应的战力评分系数。`,`number`],[`tags`,`标签`,`属性标签列表（如 DEBUFF），供 MythicMobs 按标签批量增删临时属性。`,`stringList`],[`temporary_stack_mode`,`临时叠加模式`,`作为临时属性重复施加时的叠加模式。`,`enum`,{options:[`REPLACE`,`STACK`],optionLabelPrefix:`temporary_stack_mode`}],[`child_bonuses`,`子属性加成`,`父级属性按子属性 ID 提供的加成映射。`,`dynamic_map`,{creatableChildren:!0}]],m=[[`id`,`ID`,`伤害类型唯一标识。`,`text`],[`display_name`,`显示名称`,`伤害类型展示名称。`,`text`],[`description`,`说明`,`伤害类型说明。`,`text`],[`aliases`,`别名`,`可引用为该伤害类型的别名列表。`,`stringList`],[`allowed_events`,`允许事件`,`允许触发该伤害类型的 Bukkit DamageCause 列表。`,`stringList`],[`hard_lock`,`硬锁定`,`是否强制接管该伤害类型对应的原版事件。`,`boolean`],[`stages`,`结算阶段`,`伤害结算的有序阶段列表。`,`objectList`],[`recovery`,`恢复规则`,`造成伤害后的吸血或资源恢复规则。`,`object`],[`attacker_message`,`攻击者消息`,`伤害结算后发送给攻击者的消息模板。`,`text`],[`target_message`,`受击者消息`,`伤害结算后发送给受击者的消息模板。`,`text`]],h=[[`id`,`ID`,`词条格式唯一标识。`,`text`],[`format`,`格式模板`,`词条显示格式，支持 {name}、{value}、{sign} 等占位符。`,`text`],[`precision`,`精度`,`数值显示的小数位数。`,`number`],[`read_priority`,`读取优先级`,`从 Lore 解析属性时的匹配优先级。`,`number`],[`read_patterns`,`读取正则`,`从 Lore 文本中提取数值的正则表达式列表。`,`stringList`]],g=[[`id`,`ID`,`条件定义唯一标识。`,`text`],[`source_id`,`来源 ID`,`条件规则来源标识，用于日志和调试追踪。`,`text`],[`schema_version`,`结构版本`,`条件规则结构版本。新条件建议使用 2。`,`number`],[`condition`,`条件块`,`PDC 条件组合逻辑与条目列表。`,`object`],[`condition.type`,`条件逻辑`,`多条件组合逻辑。`,`enum`,{options:[`all_of`,`any_of`,`none_of`,`at_least`,`exactly`],optionLabelPrefix:`conditionType`}],[`condition.required_count`,`需要满足数量`,`at_least / exactly 条件逻辑下需要满足的最少条件数量。`,`number`],[`condition.invalid_as_failure`,`解析失败视为失败`,`条件表达式解析失败时是否视为不通过。`,`boolean`],[`condition.entries`,`条件列表`,`具体条件项列表，每项包含 type、key/pattern 和 condition 表达式。`,`objectList`]],_={KILL:`击杀`,WORLD_BORDER:`世界边界`,CONTACT:`接触`,ENTITY_ATTACK:`实体攻击`,ENTITY_SWEEP_ATTACK:`横扫攻击`,PROJECTILE:`弹射物`,SUFFOCATION:`窒息`,FALL:`摔落`,FIRE:`火焰`,FIRE_TICK:`燃烧`,MELTING:`融化`,LAVA:`岩浆`,DROWNING:`溺水`,BLOCK_EXPLOSION:`方块爆炸`,ENTITY_EXPLOSION:`实体爆炸`,VOID:`虚空`,LIGHTNING:`雷击`,SUICIDE:`自杀`,STARVATION:`饥饿`,POISON:`中毒`,MAGIC:`魔法`,WITHER:`凋零`,FALLING_BLOCK:`落块`,THORNS:`荆棘反伤`,DRAGON_BREATH:`龙息`,FLY_INTO_WALL:`碰撞墙体`,HOT_FLOOR:`高温方块`,CAMPFIRE:`营火`,CRAMMING:`实体挤压`,DRYOUT:`脱水`,FREEZE:`冻结`,SONIC_BOOM:`音爆`,CUSTOM:`自定义`,OUTSIDE_BORDER:`边界外`,GENERIC:`通用`,EXPLOSION:`爆炸`,BAD_RESPAWN_POINT:`错误重生点`,OUT_OF_WORLD:`世界外`},v=(0,e.getRuntimeEnum)(`bukkit.damageCause`),y=v.length?v:Object.keys(_),b=[`FLAT_PERCENT`,`CUSTOM`],x=[`ATTACKER`,`TARGET`,`CONTEXT`],S=[`ADD`,`SUBTRACT`],C=(0,e.defineEmakiPluginWebModule)({module:{id:c,displayName:`Attribute`,summaryKey:`emakiattribute.module.summary`,icon:`attribute`,tone:`attribute`},files:[{id:`config`,path:`config.yml`,kind:`CONFIG`,titleKey:`emakiattribute.file.config.title`,commentKey:`emakiattribute.file.config.comment`},{id:`attribute-balance`,path:`attribute_balance.yml`,kind:`CONFIG`,titleKey:`emakiattribute.file.attribute_balance.title`,commentKey:`emakiattribute.file.attribute_balance.comment`},{id:`attributes`,path:`attributes/**/*.yml`,kind:`CONFIG`,titleKey:`emakiattribute.file.attributes.title`,commentKey:`emakiattribute.file.attributes.comment`},{id:`damage-types`,path:`damage_types/**/*.yml`,kind:`CONFIG`,titleKey:`emakiattribute.file.damage_types.title`,commentKey:`emakiattribute.file.damage_types.comment`},{id:`lore-formats`,path:`lore_formats/**/*.yml`,kind:`CONFIG`,titleKey:`emakiattribute.file.lore_formats.title`,commentKey:`emakiattribute.file.lore_formats.comment`},{id:`conditions`,path:`conditions/**/*.yml`,kind:`CONFIG`,titleKey:`emakiattribute.file.conditions.title`,commentKey:`emakiattribute.file.conditions.comment`},{id:`diagnostics`,path:`diagnostics`,kind:`ATTRIBUTE_DIAGNOSTICS`,titleKey:`emakiattribute.file.diagnostics.title`,commentKey:`emakiattribute.file.diagnostics.comment`}],fileKindLabels:[(0,e.defineFileKindLabel)({kind:`ATTRIBUTE_DIAGNOSTICS`,label:l(`诊断`,`Diagnostics`)})],surfaces:[(0,e.defineSurface)({kind:`ATTRIBUTE_DIAGNOSTICS`,editorId:`emakiattribute:diagnostics`,component:i,label:l(`玩家属性追踪 / 伤害调试器`,`Attribute sources / Damage trace`),priority:120})],insightDefinitions:[{pathPrefix:`attributes/`,idType:`attribute`,idPath:`id`}],config:{metaFields:u,fileSchemas:[{pathPrefix:`attribute_balance.yml`,fields:f},{pathPrefix:`attributes/`,fields:p},{pathPrefix:`damage_types/`,fields:m},{pathPrefix:`lore_formats/`,fields:h},{pathPrefix:`conditions/`,fields:g}],ruleFields:d,rules:[[{key:`value_kind`},{label:l(`数值类型`,`Value kind`),comment:`属性数值语义。`,type:`enum`,options:[`FLAT`,`PERCENT`,`CHANCE`,`REGEN`,`RESOURCE`],optionLabelPrefix:`value_kind`}],[{key:`target_type`},{label:l(`目标类型`,`Target type`),comment:`属性作用目标类型。`,type:`enum`,options:[`GENERIC`,`VANILLA`,`RESOURCE`,`DAMAGE`],optionLabelPrefix:`target_type`}],[{key:`kind`},{label:l(`阶段类型`,`Stage kind`),comment:`伤害阶段计算类型。`,type:`enum`,options:b,optionLabelPrefix:`damageStageKind`}],[{key:`source`},{label:l(`来源`,`Source`),comment:`阶段或恢复数据来源。`,type:`enum`,options:x,optionLabelPrefix:`damageStageSource`}],[{key:`resistance_source`},{label:l(`抗性来源`,`Resistance source`),comment:`恢复抗性属性的来源。`,type:`enum`,options:x,optionLabelPrefix:`damageStageSource`}],[{key:`mode`},{label:l(`计算模式`,`Mode`),comment:`阶段对输入伤害的计算模式。`,type:`enum`,options:S,optionLabelPrefix:`damageStageMode`}],[{path:`condition.type`},{label:l(`条件逻辑`,`Condition logic`),comment:`多条件组合逻辑。`,type:`enum`,options:[`all_of`,`any_of`,`none_of`,`at_least`,`exactly`],optionLabelPrefix:`conditionType`}],[{key:`curve_type`},{label:l(`曲线类型`,`Curve type`),comment:`超过阈值后使用的衰减函数类型。`,type:`enum`,options:[`logarithmic`,`sqrt`,`piecewise_linear`,`linear`],optionLabelPrefix:`curve_type`}],[{path:`condition.required_count`},{label:l(`需要满足数量`,`Required count`),comment:`at_least / exactly 条件逻辑下需要满足的最少条件数量。`,type:`number`}]],createTemplates:[[`default_profile.resources`,{id:`resource`,label:l(`资源模板`,`Resource template`),fields:[{path:`display_name`,label:`显示名称`,comment:`资源在界面中显示的名称。`,type:`text`,defaultValue:`新资源`},{path:`default_max`,label:`默认最大值`,comment:`资源默认最大值。`,type:`number`,defaultValue:100},{path:`min_max`,label:`最大值下限`,comment:`资源最大值允许的最低值。`,type:`number`,defaultValue:0},{path:`max_max`,label:`最大值上限`,comment:`资源最大值允许的最高值。`,type:`number`,defaultValue:1e3},{path:`sync_to_bukkit`,label:`同步 Bukkit`,comment:`是否同步到 Bukkit 原生属性。`,type:`boolean`,defaultValue:!1},{path:`full_on_init`,label:`初始满值`,comment:`初始化时是否填充至最大值。`,type:`boolean`,defaultValue:!0}]}],[`default_profile.attributes`,{id:`attribute`,label:l(`属性默认值`,`Attribute default value`),fields:[{path:`value`,label:`默认值`,comment:`属性默认基础数值。`,type:`number`,defaultValue:0}]}],[`scaling_curves`,{id:`curve`,label:l(`衰减曲线模板`,`Scaling curve template`),fields:[{path:`attribute`,label:`属性 ID`,comment:`需要应用衰减的属性 ID。`,type:`text`,defaultValue:`physical_attack`},{path:`threshold`,label:`阈值`,comment:`超过该值后开始衰减。`,type:`number`,defaultValue:100},{path:`curve_type`,label:`曲线类型`,comment:`超过阈值后使用的衰减函数类型。`,type:`enum`,options:[`logarithmic`,`sqrt`,`piecewise_linear`,`linear`],defaultValue:`logarithmic`},{path:`factor`,label:`系数`,comment:`衰减计算系数。`,type:`number`,defaultValue:1}]}]],listItemSchemas:[[`allowed_damage_causes`,[{path:`cause`,label:`伤害来源`,comment:`Bukkit DamageCause，选项来自当前服务端编译期 API。`,type:`enum`,options:y,optionLabelPrefix:`damageCause`},{path:`damage_type`,label:`伤害类型`,comment:`对应 damage_types/ 下的伤害类型 ID。`,type:`text`,defaultValue:`physical`},{path:`damage`,label:`基础伤害`,comment:`进入 EmakiAttribute 结算时使用的基础伤害值。`,type:`number`,defaultValue:1},{path:`enabled`,label:`启用`,comment:`是否启用此伤害来源规则。`,type:`boolean`,defaultValue:!0}],{uniqueBy:`cause`}],[`stages`,[{path:`id`,label:`阶段 ID`,comment:`伤害结算阶段的唯一 ID。`,type:`text`,defaultValue:`stage`},{path:`kind`,label:`阶段类型`,comment:`伤害阶段计算类型。`,type:`enum`,options:b,optionLabelPrefix:`damageStageKind`,defaultValue:`FLAT_PERCENT`},{path:`source`,label:`来源`,comment:`阶段读取属性的来源。`,type:`enum`,options:x,optionLabelPrefix:`damageStageSource`,defaultValue:`ATTACKER`},{path:`mode`,label:`计算模式`,comment:`阶段对输入伤害的计算模式。`,type:`enum`,options:S,optionLabelPrefix:`damageStageMode`,defaultValue:`ADD`},{path:`flat_attributes`,label:`固定属性`,comment:`参与固定值计算的属性 ID 列表。`,type:`stringList`,defaultValue:null},{path:`percent_attributes`,label:`百分比属性`,comment:`参与百分比计算的属性 ID 列表。`,type:`stringList`,defaultValue:null},{path:`chance_attributes`,label:`概率属性`,comment:`决定阶段是否触发的概率属性 ID 列表。`,type:`stringList`,defaultValue:null},{path:`multiplier_attributes`,label:`倍率属性`,comment:`触发后的倍率属性 ID 列表。`,type:`stringList`,defaultValue:null},{path:`expression`,label:`计算表达式`,comment:`自定义计算表达式。`,type:`text`,defaultValue:null},{path:`min_result`,label:`结果下限`,comment:`阶段结果允许的最小值。`,type:`number`,defaultValue:null},{path:`max_result`,label:`结果上限`,comment:`阶段结果允许的最大值。`,type:`number`,defaultValue:null},{path:`min_chance`,label:`概率下限`,comment:`阶段触发概率的最小值。`,type:`number`,defaultValue:null},{path:`max_chance`,label:`概率上限`,comment:`阶段触发概率的最大值。`,type:`number`,defaultValue:null},{path:`min_multiplier`,label:`倍率下限`,comment:`阶段倍率允许的最小值。`,type:`number`,defaultValue:null},{path:`max_multiplier`,label:`倍率上限`,comment:`阶段倍率允许的最大值。`,type:`number`,defaultValue:null}],{uniqueBy:`id`}],[`condition.entries`,[{path:`type`,label:`类型`,comment:`条件类型：pdc_meta、pdc_attribute、lore_regex 或 source_id。`,type:`enum`,options:[`pdc_meta`,`pdc_attribute`,`lore_regex`,`source_id`],defaultValue:`pdc_meta`},{path:`key`,label:`PDC 键名`,comment:`要匹配的 PDC 数据键名。`,type:`text`,defaultValue:``},{path:`pattern`,label:`正则模式`,comment:`用于从 Lore 或文本中提取数值的正则表达式。`,type:`text`,defaultValue:``},{path:`condition`,label:`判定表达式`,comment:`支持 {value}、{player_level}、{player_name} 等变量的判定表达式。`,type:`text`,defaultValue:``},{path:`require_match`,label:`必须匹配`,comment:`是否要求正则必须命中才视为通过。`,type:`boolean`,defaultValue:!0}]]]},locales:[(0,e.defineLocales)(`zh-CN`,{"emakiattribute.module.name":`Attribute`,"emakiattribute.module.summary":`属性、资源、伤害接管与曲线`,"emakiattribute.file.config.title":`主配置`,"emakiattribute.file.config.comment":`属性系统主配置，包含伤害接管、资源恢复和属性曲线。`,"emakiattribute.file.attribute_balance.title":`属性权重`,"emakiattribute.file.attribute_balance.comment":`属性语义分组、角色定位与评分权重配置。`,"emakiattribute.file.attributes.title":`属性`,"emakiattribute.file.attributes.comment":`属性文件目录，每个文件配置一个属性的 ID、类型、范围和词条格式。`,"emakiattribute.file.damage_types.title":`伤害类型`,"emakiattribute.file.damage_types.comment":`伤害类型文件目录，每个文件配置一种伤害的结算阶段和恢复规则。`,"emakiattribute.file.lore_formats.title":`词条格式`,"emakiattribute.file.lore_formats.comment":`词条格式文件目录，每个文件配置一种属性在物品 Lore 中的显示模板。`,"emakiattribute.file.conditions.title":`PDC 条件`,"emakiattribute.file.conditions.comment":`PDC 属性读取条件目录，控制物品属性在何种条件下生效。`,"emakiattribute.file.diagnostics.title":`属性诊断`,"emakiattribute.file.diagnostics.comment":`查询在线玩家的属性来源与最近伤害 Trace。`,"emakiattribute.diagnostics.toolbar.title":`玩家属性追踪 / 伤害调试器`,"emakiattribute.diagnostics.toolbar.subtitle":`查询在线玩家的属性来源与最近伤害 Trace`,...Object.fromEntries(u.flatMap(([e,t,n])=>[[`emakiattribute.field.${e}`,t],[`emakiattribute.comment.${e}`,n]])),...Object.fromEntries([...f,...p,...m,...h,...g].flatMap(([e,t,n])=>[[`emakiattribute.field.${e}`,t],[`emakiattribute.comment.${e}`,n]])),...Object.fromEntries(Object.entries(d).flatMap(([e,[t,n]])=>[[`emakiattribute.field.${e}`,t],[`emakiattribute.comment.${e}`,n]])),...Object.fromEntries(Object.entries(_).map(([e,t])=>[`emakiattribute.option.damageCause.${e}`,t])),"emakiattribute.option.value_kind.FLAT":`固定值`,"emakiattribute.option.value_kind.PERCENT":`百分比`,"emakiattribute.option.value_kind.CHANCE":`概率`,"emakiattribute.option.value_kind.REGEN":`回复`,"emakiattribute.option.value_kind.RESOURCE":`资源`,"emakiattribute.option.target_type.GENERIC":`通用`,"emakiattribute.option.target_type.VANILLA":`原版属性`,"emakiattribute.option.target_type.RESOURCE":`资源`,"emakiattribute.option.target_type.DAMAGE":`伤害`,"emakiattribute.option.temporary_stack_mode.REPLACE":`覆盖`,"emakiattribute.option.temporary_stack_mode.STACK":`相加`,"emakiattribute.option.damageStageKind.FLAT_PERCENT":`固定值 + 百分比`,"emakiattribute.option.damageStageKind.CUSTOM":`自定义表达式`,"emakiattribute.option.damageStageSource.ATTACKER":`攻击者`,"emakiattribute.option.damageStageSource.TARGET":`目标`,"emakiattribute.option.damageStageSource.CONTEXT":`上下文`,"emakiattribute.option.damageStageMode.ADD":`加算`,"emakiattribute.option.damageStageMode.SUBTRACT":`减算`}),(0,e.defineLocales)(`en-US`,{"emakiattribute.module.name":`Attribute`,"emakiattribute.module.summary":`Attributes, resources, hard damage handling, and curves`,"emakiattribute.file.config.title":`Main Config`,"emakiattribute.file.config.comment":`Main attribute system configuration covering damage handling, resource recovery, and scaling curves.`,"emakiattribute.file.attribute_balance.title":`Attribute Weights`,"emakiattribute.file.attribute_balance.comment":`Semantic grouping, role positioning, and scoring weights for attributes.`,"emakiattribute.file.attributes.title":`Attributes`,"emakiattribute.file.attributes.comment":`Directory of attribute files. Each file configures an attribute ID, type, range, and lore format.`,"emakiattribute.file.damage_types.title":`Damage Types`,"emakiattribute.file.damage_types.comment":`Directory of damage type files. Each file configures a damage settlement stage and recovery rules.`,"emakiattribute.file.lore_formats.title":`Lore Formats`,"emakiattribute.file.lore_formats.comment":`Directory of lore format files. Each file configures the display template for an attribute in item lore.`,"emakiattribute.file.conditions.title":`PDC Conditions`,"emakiattribute.file.conditions.comment":`Directory of PDC attribute read conditions that control when item attributes take effect.`,"emakiattribute.file.diagnostics.title":`Attribute Diagnostics`,"emakiattribute.file.diagnostics.comment":`Query online player attribute sources and recent damage traces.`,"emakiattribute.diagnostics.toolbar.title":`Attribute sources / Damage trace`,"emakiattribute.diagnostics.toolbar.subtitle":`Query attribute sources and recent damage traces for an online player`,"emakiattribute.field.hard_lock_damage":`Hard-lock Damage`,"emakiattribute.field.default_damage_type":`Default Damage Type`,"emakiattribute.field.vanilla_event_damage":`Vanilla Event Damage`,"emakiattribute.field.vanilla_event_damage.enabled":`Enable Vanilla Damage Handling`,"emakiattribute.field.vanilla_event_damage.damage_type":`Damage Type`,"emakiattribute.field.default_profile":`Default Profile`,"emakiattribute.field.scaling_curves":`Scaling Curves`,"emakiattribute.field.allowed_damage_causes":`Allowed Damage Causes`,"emakiattribute.field.allowed_damage_causes.cause":`Cause`,"emakiattribute.field.allowed_damage_causes.damage_type":`Damage Type`,"emakiattribute.field.allowed_damage_causes.damage":`Base Damage`,"emakiattribute.field.allowed_damage_causes.enabled":`Enabled`,"emakiattribute.option.damageStageKind.FLAT_PERCENT":`Flat + Percent`,"emakiattribute.option.damageStageKind.CUSTOM":`Custom Expression`,"emakiattribute.option.damageStageSource.ATTACKER":`Attacker`,"emakiattribute.option.damageStageSource.TARGET":`Target`,"emakiattribute.option.damageStageSource.CONTEXT":`Context`,"emakiattribute.option.damageStageMode.ADD":`Add`,"emakiattribute.option.damageStageMode.SUBTRACT":`Subtract`,"emakiattribute.option.temporary_stack_mode.REPLACE":`Replace`,"emakiattribute.option.temporary_stack_mode.STACK":`Stack`})],capabilities:(0,e.defineCapabilities)([`config`,`insight`,`diagnostics`]),diagnostics:[{id:`emakiattribute.manifest-v2`,description:`Attribute registers config, diagnostics surface, and insight metadata through Manifest v2.`,severity:`info`},{id:`emakiattribute.damage-schema`,description:`Attribute damage stages, PDC conditions, resources, and scaling curves are represented in Manifest v2 config metadata.`,severity:`info`}]}),w=!1;function T(){w||(w=!0,r(),(0,e.registerEmakiPluginWebModule)(C))}T()})(EmakiWebConsole,React);