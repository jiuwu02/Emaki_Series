(function(e,t){var n=Object.create,r=Object.defineProperty,i=Object.getOwnPropertyDescriptor,a=Object.getOwnPropertyNames,o=Object.getPrototypeOf,s=Object.prototype.hasOwnProperty,c=(e,t,n,o)=>{if(t&&typeof t==`object`||typeof t==`function`)for(var c=a(t),l=0,u=c.length,d;l<u;l++)d=c[l],!s.call(e,d)&&d!==n&&r(e,d,{get:(e=>t[e]).bind(null,d),enumerable:!(o=i(t,d))||o.enumerable});return e};t=((e,t,i)=>(i=e==null?{}:n(o(e)),c(t||!e||!e.__esModule?r(i,`default`,{value:e,enumerable:!0}):i,e)))(t,1);var l=`.strengthen-route-preview {
  margin-top: 16px;
  display: grid;
  gap: 12px;
  padding: 14px;
  color: var(--text);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 6px;
}

.strengthen-route-preview__header,
.strengthen-route-preview__actions,
.strengthen-route-preview__summary,
.strengthen-route-preview__node-actions,
.strengthen-route-preview__detail-meta {
  display: flex;
  gap: 8px;
}

.strengthen-route-preview__header {
  justify-content: space-between;
  align-items: flex-start;
}

.strengthen-route-preview__title {
  margin: 0;
  font-size: 16px;
  line-height: 1.35;
  letter-spacing: -0.01em;
}

.strengthen-route-preview__hint,
.strengthen-route-preview__empty,
.strengthen-route-preview__detail-note {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.45;
}

.strengthen-route-preview__summary {
  flex-wrap: wrap;
  color: var(--muted);
  font-size: 12px;
}

.strengthen-route-preview__summary strong,
.strengthen-route-preview__detail strong {
  color: var(--text);
}

.strengthen-route-preview__warning {
  padding: 9px 10px;
  color: var(--amber);
  background: var(--changed-soft);
  border: 1px solid var(--changed-line);
  border-radius: 6px;
  font-size: 12px;
}

.strengthen-route-preview__graph-scroll,
.strengthen-route-preview__table-scroll {
  overflow-x: auto;
}

.strengthen-route-preview__graph {
  display: block;
  width: 100%;
  min-width: 720px;
  background: var(--input);
  border: 1px solid var(--line);
  border-radius: 6px;
}

.strengthen-route-preview__detail-grid {
  display: grid;
  grid-template-columns: minmax(360px, 1.2fr) minmax(280px, .8fr);
  gap: 12px;
}

.strengthen-route-preview__table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
  table-layout: fixed;
  color: var(--text);
  font-size: 12px;
}

.strengthen-route-preview__table th,
.strengthen-route-preview__table td {
  padding: 7px 9px;
  border-bottom: 1px solid color-mix(in oklch, var(--line) 74%, transparent);
  text-align: left;
  vertical-align: middle;
}

.strengthen-route-preview__table th {
  color: var(--muted);
  font-weight: 700;
}

.strengthen-route-preview__select-cell {
  width: 44px;
}

.strengthen-route-preview__select-button {
  width: 100%;
  min-height: 28px;
  color: var(--muted);
  background: transparent;
  border: 1px solid var(--line);
  border-radius: 5px;
  cursor: pointer;
}

.strengthen-route-preview__select-button[aria-pressed="true"] {
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-color: var(--accent);
}

.strengthen-route-preview__select-button:focus-visible,
.strengthen-route-preview__node-button:focus-visible {
  outline: 1px solid var(--accent);
  outline-offset: 2px;
}

.strengthen-route-preview__row--selected td {
  background: var(--accent-soft);
}

.strengthen-route-preview__detail {
  min-width: 0;
  padding: 12px;
  background: color-mix(in oklch, var(--surface-2) 72%, transparent);
  border: 1px solid var(--line);
  border-radius: 6px;
}

.strengthen-route-preview__detail h4,
.strengthen-route-preview__detail h5,
.strengthen-route-preview__detail p {
  margin: 0;
}

.strengthen-route-preview__detail h4 {
  font-size: 14px;
  line-height: 1.35;
}

.strengthen-route-preview__detail h5 {
  margin-top: 12px;
  color: var(--muted);
  font-size: 11px;
  letter-spacing: .05em;
  text-transform: uppercase;
}

.strengthen-route-preview__detail ul {
  margin: 6px 0 0;
  padding-left: 18px;
}

.strengthen-route-preview__detail pre {
  margin: 6px 0 0;
  padding: 8px;
  max-height: 180px;
  overflow: auto;
  white-space: pre-wrap;
  color: var(--text);
  background: var(--input);
  border: 1px solid var(--line);
  border-radius: 6px;
  font-size: 12px;
}

.strengthen-route-preview__detail code,
.strengthen-route-preview__table code {
  color: var(--accent-strong);
  font-family: ui-monospace, Cascadia Code, monospace;
}

.strengthen-route-preview__node-actions {
  flex-wrap: wrap;
}

.strengthen-route-preview__node-button {
  min-height: 30px;
  padding: 4px 8px;
  color: var(--muted);
  background: transparent;
  border: 1px solid var(--line);
  border-radius: 999px;
  cursor: pointer;
}

.strengthen-route-preview__node-button[aria-pressed="true"] {
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-color: var(--accent);
}

.strengthen-route-preview__diagnostic {
  margin-top: 8px;
  color: var(--faint);
  font-size: 11px;
}

.strengthen-route-preview__diagnostic pre {
  margin: 6px 0 0;
  white-space: pre-wrap;
}

@media (max-width: 880px) {
  .strengthen-route-preview__header,
  .strengthen-route-preview__detail-grid {
    grid-template-columns: 1fr;
  }

  .strengthen-route-preview__header {
    display: grid;
  }

  .strengthen-route-preview__actions {
    flex-wrap: wrap;
  }

  .strengthen-route-preview__graph-scroll,
  .strengthen-route-preview__table-scroll {
    margin-inline: -2px;
    padding-bottom: 4px;
  }

  .strengthen-route-preview__actions .ui-button,
  .strengthen-route-preview__select-button,
  .strengthen-route-preview__node-button {
    min-height: 44px;
  }

  .strengthen-route-preview__table {
    min-width: 680px;
  }
}
`;function u(){(0,e.injectExtensionStyles)(`emakistrengthen-route-preview`,l)}var d=e.localeText;function f({api:n,data:r}){let i=typeof r?.id==`string`?r.id:``,[a,o]=(0,t.useState)(null),[s,c]=(0,t.useState)(``),[l,u]=(0,t.useState)(!1),[f,v]=(0,t.useState)(``),y=(0,t.useMemo)(()=>a?.nodes.find(e=>e.id===s)??a?.nodes[0],[a,s]),b=async()=>{u(!0),v(``);try{let e=await n.pluginApi(`strengthen`,`route-preview`,{recipeId:i}),t={recipeId:String(e.recipeId??i),displayName:String(e.displayName??``),branching:!!e.branching,maxStar:Number(e.maxStar??0),nodes:Array.isArray(e.nodes)?e.nodes:[],edges:Array.isArray(e.edges)?e.edges:[],warnings:Array.isArray(e.warnings)?e.warnings:[]};o(t),c(t.nodes[0]?.id??``)}catch(e){v(e instanceof Error?e.message:String(e))}finally{u(!1)}};return(0,t.useEffect)(()=>{i&&b()},[i]),t.default.createElement(`section`,{className:`strengthen-route-preview`},t.default.createElement(`div`,{className:`strengthen-route-preview__header`},t.default.createElement(`div`,null,t.default.createElement(`h3`,{className:`strengthen-route-preview__title`},d(`强化路线蓝图`,`Strengthen route blueprint`)),t.default.createElement(`p`,{className:`strengthen-route-preview__hint`},d(`基于服务器已加载配方生成，只读预览路线、材料、成功率与属性累计。结构化配置仍可继续编辑，预览只反映运行时计算结果。`,`Generated from the loaded server recipe; read-only preview of route, materials, success rates, and cumulative stats. Structured config remains editable; the preview reflects runtime calculation only.`))),t.default.createElement(`div`,{className:`strengthen-route-preview__actions`},t.default.createElement(e.Button,{size:`sm`,onClick:b,disabled:l||!i},l?d(`加载中...`,`Loading...`):d(`刷新预览`,`Refresh preview`)),t.default.createElement(e.Button,{size:`sm`,variant:`soft`,onClick:()=>{if(!a)return;let e=new Blob([JSON.stringify(a,null,2)],{type:`application/json;charset=utf-8`}),t=URL.createObjectURL(e),n=document.createElement(`a`);n.href=t,n.download=`${a.recipeId||`strengthen-route`}-preview.json`,n.click(),URL.revokeObjectURL(t)},disabled:!a},d(`导出 JSON`,`Export JSON`)))),i?null:t.default.createElement(`div`,{className:`strengthen-route-preview__empty`},d(`当前文件未配置 id，无法匹配运行时配方。`,`This file has no id, so no loaded recipe can be matched.`)),f?t.default.createElement(p,{route:`route-preview`,message:f}):null,a?t.default.createElement(t.default.Fragment,null,t.default.createElement(`div`,{className:`strengthen-route-preview__summary`,"aria-label":d(`路线摘要`,`Route summary`)},t.default.createElement(`span`,null,d(`配方`,`Recipe`),`: `,t.default.createElement(`strong`,null,a.displayName||a.recipeId)),t.default.createElement(`span`,null,d(`节点`,`Nodes`),`: `,t.default.createElement(`strong`,null,a.nodes.length)),t.default.createElement(`span`,null,d(`连线`,`Edges`),`: `,t.default.createElement(`strong`,null,a.edges.length)),t.default.createElement(`span`,null,d(`分支`,`Branching`),`: `,t.default.createElement(`strong`,null,a.branching?d(`是`,`Yes`):d(`否`,`No`)))),(a.warnings??[]).length?t.default.createElement(`div`,{className:`strengthen-route-preview__warning`,role:`status`},a.warnings?.map(e=>e.message).join(` / `)):null,t.default.createElement(`div`,{className:`strengthen-route-preview__graph-scroll`},t.default.createElement(m,{nodes:a.nodes,edges:a.edges,selectedId:y?.id??``})),t.default.createElement(h,{nodes:a.nodes,selectedId:y?.id??``,onSelect:c}),t.default.createElement(`div`,{className:`strengthen-route-preview__detail-grid`},t.default.createElement(g,{nodes:a.nodes,selectedId:y?.id??``,onSelect:c}),y?t.default.createElement(_,{node:y}):null)):t.default.createElement(`div`,{className:`strengthen-route-preview__empty`},d(`暂无路线数据。`,`No route data.`)))}function p({route:n,message:r}){return t.default.createElement(e.InlineError,null,t.default.createElement(`strong`,null,d(`强化路线预览暂不可用。`,`Strengthen route preview is unavailable.`)),t.default.createElement(`p`,null,d(`插件 API strengthen/${n} 请求失败；当前 YAML 仍可继续编辑，保存不依赖此只读预览。`,`Plugin API strengthen/${n} failed. The current YAML remains editable; saving does not depend on this read-only preview.`)),t.default.createElement(`details`,{className:`strengthen-route-preview__diagnostic`},t.default.createElement(`summary`,null,d(`开发者诊断`,`Developer diagnostics`)),t.default.createElement(`pre`,null,r)))}function m({nodes:e,edges:n,selectedId:r}){let i=Math.max(240,90+e.length*18),a=v(e,920,i);return t.default.createElement(`svg`,{viewBox:`0 0 920 ${i}`,className:`strengthen-route-preview__graph`,role:`img`,"aria-label":d(`强化路线图，节点可在下方列表和表格中选择。`,`Strengthen route graph. Nodes can be selected in the list and table below.`)},n.map((e,n)=>{let r=a.get(e.from),i=a.get(e.to);if(!r||!i)return null;let o=e.type===`branch`;return t.default.createElement(`g`,{key:`${e.from}-${e.to}-${n}`},t.default.createElement(`line`,{x1:r.x,y1:r.y,x2:i.x,y2:i.y,stroke:o?`var(--amber)`:`color-mix(in oklch, var(--muted) 62%, transparent)`,strokeWidth:o?3:2,strokeDasharray:o?`5 4`:void 0}),e.label?t.default.createElement(`text`,{x:(r.x+i.x)/2,y:(r.y+i.y)/2-6,fill:`var(--amber)`,fontSize:`12`,textAnchor:`middle`},x(e.label)):null)}),e.map(e=>{let n=a.get(e.id),i=e.id===r;return t.default.createElement(`g`,{key:e.id,"aria-hidden":`true`},t.default.createElement(`circle`,{cx:n.x,cy:n.y,r:i?18:15,fill:i?`var(--accent)`:`var(--surface-2)`,stroke:e.branchPath?`var(--amber)`:`var(--accent-strong)`,strokeWidth:i?3:2}),t.default.createElement(`text`,{x:n.x,y:n.y+4,textAnchor:`middle`,fontSize:`12`,fill:i?`var(--bg)`:`var(--text)`,fontWeight:700},e.star),t.default.createElement(`text`,{x:n.x,y:n.y+34,textAnchor:`middle`,fontSize:`11`,fill:`var(--muted)`},b(e.branchPath||`root`)))}))}function h({nodes:e,selectedId:n,onSelect:r}){return e.length?t.default.createElement(`div`,{className:`strengthen-route-preview__node-actions`,"aria-label":d(`选择路线节点`,`Select route node`)},e.map(e=>t.default.createElement(`button`,{key:e.id,type:`button`,className:`strengthen-route-preview__node-button`,"aria-pressed":e.id===n,onClick:()=>r(e.id)},`★`,e.star,` · `,b(e.branchPath||`root`)))):null}function g({nodes:e,selectedId:n,onSelect:r}){return t.default.createElement(`div`,{className:`strengthen-route-preview__table-scroll`},t.default.createElement(`table`,{className:`strengthen-route-preview__table`},t.default.createElement(`thead`,null,t.default.createElement(`tr`,null,t.default.createElement(`th`,{className:`strengthen-route-preview__select-cell`},d(`选择`,`Select`)),t.default.createElement(`th`,null,`★`),t.default.createElement(`th`,null,d(`分支`,`Branch`)),t.default.createElement(`th`,null,d(`成功率`,`Rate`)),t.default.createElement(`th`,null,d(`材料`,`Materials`)),t.default.createElement(`th`,null,d(`增量`,`Delta`)))),t.default.createElement(`tbody`,null,e.map(e=>t.default.createElement(`tr`,{key:e.id,className:e.id===n?`strengthen-route-preview__row--selected`:``},t.default.createElement(`td`,{className:`strengthen-route-preview__select-cell`},t.default.createElement(`button`,{type:`button`,className:`strengthen-route-preview__select-button`,"aria-pressed":e.id===n,onClick:()=>r(e.id)},e.id===n?d(`当前`,`Now`):d(`选择`,`Pick`))),t.default.createElement(`td`,null,e.star),t.default.createElement(`td`,null,t.default.createElement(`code`,null,e.branchPath||`root`)),t.default.createElement(`td`,null,S(e.successRate),`%`),t.default.createElement(`td`,null,e.materials.map(e=>`${e.item} x${e.amount}`).join(`, `)||`-`),t.default.createElement(`td`,null,y(e.statsDelta)))))))}function _({node:e}){return t.default.createElement(`aside`,{className:`strengthen-route-preview__detail`},t.default.createElement(`h4`,null,`★`,e.star,` · `,e.stageName||e.branchName||e.branchId),t.default.createElement(`p`,{className:`strengthen-route-preview__detail-note`},d(`分支路径`,`Branch path`),`: `,t.default.createElement(`code`,null,e.branchPath||`root`)),t.default.createElement(`div`,{className:`strengthen-route-preview__detail-meta`},t.default.createElement(`span`,null,d(`成功率`,`Success rate`),`: `,t.default.createElement(`strong`,null,S(e.successRate),`%`)),t.default.createElement(`span`,null,d(`成功动作`,`Success actions`),`: `,e.hasSuccessActions?d(`有`,`Yes`):d(`无`,`No`)),t.default.createElement(`span`,null,d(`失败动作`,`Failure actions`),`: `,e.hasFailureActions?d(`有`,`Yes`):d(`无`,`No`))),t.default.createElement(`h5`,null,d(`材料`,`Materials`)),t.default.createElement(`ul`,null,e.materials.map((e,n)=>t.default.createElement(`li`,{key:n},t.default.createElement(`code`,null,e.item),` x`,e.amount,e.optional?` · ${d(`可选`,`Optional`)}`:``,e.protection?` · ${d(`保护`,`Protection`)}`:``,e.temperBoost?` · +${e.temperBoost} ${d(`锻印`,`Temper`)}`:``))),t.default.createElement(`h5`,null,d(`本级变量增量`,`Stage stat delta`)),t.default.createElement(`pre`,null,JSON.stringify(e.statsDelta,null,2)),t.default.createElement(`h5`,null,d(`累计变量`,`Cumulative stats`)),t.default.createElement(`pre`,null,JSON.stringify(e.cumulativeStats,null,2)),t.default.createElement(`h5`,null,d(`累计 EA 属性`,`Cumulative EA attributes`)),t.default.createElement(`pre`,null,JSON.stringify(e.cumulativeAttributes,null,2)),e.skillIds.length?t.default.createElement(t.default.Fragment,null,t.default.createElement(`h5`,null,d(`技能`,`Skills`)),t.default.createElement(`p`,null,e.skillIds.join(`, `))):null)}function v(e,t,n){let r=new Map;e.forEach(e=>r.set(e.star,[...r.get(e.star)??[],e]));let i=[...r.keys()].sort((e,t)=>e-t),a=new Map;return i.forEach((e,o)=>{let s=r.get(e)??[];s.forEach((e,r)=>a.set(e.id,{x:52+o/Math.max(1,i.length-1)*(t-104),y:58+(r+1)/(s.length+1)*(n-116)}))}),a}function y(e){let t=Object.entries(e??{});return t.length?t.slice(0,3).map(([e,t])=>`${e}+${S(t)}`).join(`, `):`-`}function b(e){return e.length>16?`…${e.slice(-15)}`:e}function x(e){return String(e??``).replace(/<[^>]+>/g,``)}function S(e){return Number.isFinite(e)?e.toLocaleString(void 0,{maximumFractionDigits:2}):`-`}var C={"emakistrengthen.module.name":`Strengthen`,"emakistrengthen.module.summary":`Stars, broadcasts, and success rates`,"emakistrengthen.file.config.title":`Main Config`,"emakistrengthen.file.config.comment":`Main strengthen configuration covering success rates, materials, economy, and display strategy.`,"emakistrengthen.file.gui.title":`GUI Templates`,"emakistrengthen.file.gui.comment":`Strengthen GUI template controlling target item, materials, confirm button, and hint items.`,"emakistrengthen.file.recipes.title":`Recipe Files`,"emakistrengthen.file.recipes.comment":`Strengthen recipe directory covering star stages, branch paths, materials, success rates, and actions.`,"emakistrengthen.filePath.recipes_example_recipe.title":`Sample Recipe`,"emakistrengthen.filePath.recipes_example_recipe.comment":`Linear strengthen recipe example showing star stages, materials, temper, failure handling, and a source_ids binding to the sample item.`,"emakistrengthen.filePath.recipes_example_branch_recipe.title":`Sample Branch Recipe`,"emakistrengthen.filePath.recipes_example_branch_recipe.comment":`Branch strengthen recipe example showing route choices, child branches, stage inheritance, and an explicit source_ids binding for the sample branch item.`,"emakistrengthen.filePath.gui_strengthen_gui.title":`Strengthen GUI`,"emakistrengthen.filePath.gui_strengthen_gui.comment":`Strengthen GUI template controlling target item, materials, and confirm slots.`,"emakistrengthen.file.plugin.title":`Plugin Description`,"emakistrengthen.file.plugin.comment":`plugin.yml metadata, commands, permissions, and dependencies declarations.`,"emakistrengthen.file.web-console.title":`WebUIEdit Registration`,"emakistrengthen.file.web-console.comment":`File groups, editor kinds, and frontend extension entries exposed to WebUIEdit by this plugin.`,"emakistrengthen.surface.gui":`Strengthen GUI`,"emakistrengthen.field.local_broadcast_radius":`Local Broadcast Radius`,"emakistrengthen.field.broadcast":`Broadcast`,"emakistrengthen.field.broadcast.local_stars":`Local Stars`,"emakistrengthen.field.broadcast.global_stars":`Global Stars`,"emakistrengthen.field.success_rates":`Success Rates`,"emakistrengthen.field.target_item":`Target Item`,"emakistrengthen.field.material":`Material`,"emakistrengthen.field.confirm":`Confirm`,"emakistrengthen.field.effects":`Effects`,"emakistrengthen.field.variables":`Variables`,"emakistrengthen.field.ea_attributes":`EA Attributes`,"emakistrengthen.field.es_skills":`ES Skills`,"emakistrengthen.field.name_actions":`Name Actions`,"emakistrengthen.field.lore_actions":`Lore Actions`,"emakistrengthen.field.stars":`Star Stages`,"emakistrengthen.field.children":`Child Branches`},w=`EmakiStrengthen`,T=e.localeText,E=(0,e.payloadEffectDefinition)(`ea_attribute`,`EA 属性`,[{key:`ea_attributes`,type:`map`,label:`EA 属性`,defaultValue:{}}]),D=(0,e.payloadEffectDefinition)(`es_skill`,`ES 技能`,[{key:`es_skills`,type:`stringList`,label:`ES 技能`,defaultValue:[]}]),O=[(0,e.coreEffectDefinition)(`variables`),E,D,(0,e.coreEffectDefinition)(`name_action`),(0,e.coreEffectDefinition)(`lore_action`)],k=(0,e.standardMaterialCostFields)({overrides:{item_sources:{label:`物品来源`,comment:`强化材料的 ItemSource 列表。`,defaultValue:[`minecraft-copper_ingot`]},amount:{label:`数量`,comment:`需要消耗的材料数量；-1 表示只检测不消耗。`,defaultValue:1},optional:{label:`可选`,comment:`是否为可选材料。`,defaultValue:!1},protection:{label:`保护材料`,comment:`失败时提供保护效果的材料。`,defaultValue:!1}},insertAfter:{protection:{path:`temper_boost`,label:`锻印提升`,comment:`放入后额外增加的锻印等级。`,type:`number`,defaultValue:0}}}),A=(0,e.standardCurrencyCostFields)({overrides:{provider:{label:`经济提供器`,comment:`经济系统提供器，例如 vault。`,defaultValue:`vault`},currency_id:{label:`货币 ID`,comment:`多货币系统的货币标识；留空使用默认货币。`,defaultValue:``},base_cost:{label:`基础费用`,comment:`强化经济消耗的基础数值。`,defaultValue:0},cost_formula:{label:`费用公式`,comment:`根据星级等变量计算最终费用的公式。`,defaultValue:``},display_name:{label:`显示名称`,comment:`货币在提示中的显示名称。`,defaultValue:``}}}),j=[{path:`name`,label:`阶段名称`,comment:`该星级的里程碑名称，可留空。`,type:`text`,defaultValue:``},{path:`variables`,label:`变量`,comment:`表达式变量或属性增量，源码从星级阶段顶层 variables 读取；属性计算应保持数值结果，name/lore actions 文本模板可使用随机文本、随机字符、权重随机字符和条件字符。`,type:`variablesMap`,defaultValue:{}},{path:`ea_attributes`,label:`EA 属性`,comment:`显式 EmakiAttribute 属性覆盖，源码从星级阶段顶层 ea_attributes 读取。`,type:`dynamic_map`,defaultValue:{}},{path:`effects`,label:`效果`,comment:`技能或显示动作效果列表，按 type 分流为变量、EA 属性、ES 技能、名称/Lore 动作。`,type:`effects`,defaultValue:[]},{path:`materials`,label:`材料`,comment:`强化到该星级需要的材料列表。`,type:`objectList`,defaultValue:[],itemFields:k},{path:`economy_override.currencies`,label:`经济覆盖`,comment:`该星级专属货币消耗；留空时使用配方 economy。`,type:`objectList`,defaultValue:[],itemFields:A},{path:`actions.success`,label:`成功动作`,comment:`强化成功到该星级后执行的动作。`,type:`stringList`,defaultValue:[]},{path:`actions.failure`,label:`失败动作`,comment:`强化失败后按结果星级读取的动作。`,type:`stringList`,defaultValue:[]}],M=[{path:`branch_id`,label:`分支 ID`,comment:`分支唯一标识，root 节点可使用 root。`,type:`text`,defaultValue:`branch`},{path:`display_name`,label:`显示名称`,comment:`分支在 GUI 或提示中的显示名称。`,type:`text`,defaultValue:T(`<yellow>新分支</yellow>`,`<yellow>New branch</yellow>`)},{path:`fork_after_star`,label:`分叉星级`,comment:`-1 表示不再分叉；有 children 时表示完成该星级后选择路线。`,type:`number`,defaultValue:-1},{path:`stars`,label:`星级阶段`,comment:`该分支内的星级阶段。`,type:`object`,defaultValue:{}},{path:`children`,label:`子分支`,comment:`此分支后续可选择的子路线。`,type:`object`,defaultValue:{}}],N={id:`star-success-rate`,label:T(`目标星级成功率`,`Target star success rate`),fields:[{path:`value`,label:`成功率`,comment:`该目标星级的强化成功率百分比，例如 75.0。`,type:`number`,defaultValue:100}]},P={id:`star-stage`,label:T(`星级阶段`,`Star stage`),fields:j},F={id:`branch-node`,label:T(`分支节点`,`Branch node`),fields:M},I=[[`language`,`语言`,`语言文件 ID，对应 lang/<language>.yml。`,`text`],[`version`,`配置版本`,`默认配置结构版本，通常不建议手动修改。`,`text`],[`local_broadcast_radius`,`本地广播半径`,`强化达到本地广播星级时，附近玩家可收到提示的半径，单位方块格。`,`number`],[`broadcast`,`广播设置`,`强化成功时的本地广播与全服广播触发星级设置。`,`object`],[`broadcast.local_stars`,`本地广播星级`,`强化成功达到这些星级时向附近玩家广播。`,`list`],[`broadcast.global_stars`,`全服广播星级`,`强化成功达到这些星级时向全服广播。`,`list`],[`success_rates`,`全局成功率`,`配方未单独覆盖时使用的全局强化成功率表，键为目标星级，值为百分比。`,`object`],[`effects`,`效果`,`强化阶段效果列表，用于追加变量、EA 属性或 ES 技能。`,`effects`]],L=[[`id`,`ID`,`强化配方唯一标识。`,`text`],[`display_name`,`显示名称`,`配方在 GUI、日志或提示中显示的名称。`,`text`],[`gui_template`,`GUI 模板`,`使用的强化 GUI 模板 ID。`,`text`],[`economy`,`经济消耗`,`强化经济消耗配置。`,`object`],[`economy.enabled`,`启用经济`,`是否启用该配方的经济消耗。`,`boolean`],[`economy.currencies`,`货币消耗`,`强化消耗的货币列表。`,`objectList`],[`limits`,`限制`,`强化等级、星级或次数限制。`,`object`],[`limits.max_star`,`最大星级`,`该配方允许强化到的最高星级。`,`number`],[`limits.max_temper`,`最大锻印`,`失败累积锻印的最大等级。`,`number`],[`limits.temper_chance_bonus_per_level`,`锻印成功率加成`,`每级锻印提供的成功率加成百分比。`,`number`],[`limits.success_chance_cap`,`成功率上限`,`基础成功率和锻印加成后的最高成功率。`,`number`],[`success_rates`,`成功率覆盖`,`该配方按目标星级覆盖的成功率表。`,`object`,{creatableChildren:!0,createTemplates:[N]}],[`match`,`匹配规则`,`可强化物品的来源、槽位组、Lore 或属性匹配规则。推荐优先用 source_ids 精确绑定物品源。`,`object`],[`match.source_types`,`来源类型`,`允许匹配的物品来源类型。`,`stringList`],[`match.source_ids`,`来源 ID`,`推荐的精确绑定方式；填写 EmakiItem / ItemSource 的来源 ID，避免泛匹配所有同类装备。`,`stringList`],[`match.source_patterns`,`来源模式`,`允许匹配的来源通配或正则模式。`,`stringList`],[`match.slot_groups`,`槽位组`,`可选的粗粒度类型组兜底，可选值 weapon / armor / offhand / generic；不是 main_hand / helmet 这类具体装备槽位。`,`stringList`],[`match.lore_contains`,`Lore 包含`,`物品 Lore 需要包含的文本。`,`stringList`],[`match.stats_any`,`任意属性`,`物品拥有任意一个属性时允许匹配。`,`stringList`],[`stat_lines`,`属性行`,`强化属性行模板定义。`,`object`],[`stars`,`星级阶段`,`每个目标星级的材料、属性、技能和动作配置。`,`object`,{creatableChildren:!0,createTemplates:[P]}],[`branch_tree`,`分支树`,`分支强化路线配置。`,`object`],[`branch_tree.stars`,`根分支星级`,`分支树根节点内的星级阶段。`,`object`,{creatableChildren:!0,createTemplates:[P]}],[`branch_tree.children`,`子分支`,`根分支后可选择的路线。`,`object`,{creatableChildren:!0,createTemplates:[F]}],[`condition`,`强化条件`,`执行强化前检查的条件判定块；仅用于判定，不执行 on_pass/on_fail 动作。`,`object`],[`condition.type`,`条件逻辑`,`条件表达式组合方式。`,`enum`,{options:[`all_of`,`any_of`,`none_of`,`at_least`,`exactly`],optionLabelPrefix:`conditionType`}],[`condition.entries`,`条件表达式`,`CoreLib 条件表达式字符串列表。`,`stringList`],[`condition.required_count`,`需要满足数量`,`at_least / exactly 场景下需要满足的最少条件数量。`,`number`],[`name_actions`,`名称动作`,`强化成功后对物品显示名称执行的动作。`,`actions`],[`lore_actions`,`Lore 动作`,`强化成功后对物品 Lore 执行的动作。`,`actions`],[`effects`,`效果`,`强化完成后追加的效果列表，支持变量、EA 属性和 ES 技能。`,`effects`]],R=Object.fromEntries([[`emakistrengthen.module.name`,`Strengthen`],[`emakistrengthen.module.summary`,`星级、广播、成功率`],[`emakistrengthen.file.config.title`,`主配置`],[`emakistrengthen.file.config.comment`,`强化系统主配置，包含成功率、材料、经济和显示策略。`],[`emakistrengthen.file.gui.title`,`GUI 模板`],[`emakistrengthen.file.gui.comment`,`强化界面 GUI 模板，控制目标物品、材料、确认按钮和提示物品。`],[`emakistrengthen.file.recipes.title`,`配方文件`],[`emakistrengthen.file.recipes.comment`,`强化配方目录，配置星级阶段、分支路线、材料、成功率和动作。`],[`emakistrengthen.filePath.recipes_example_recipe.title`,`示例配方`],[`emakistrengthen.filePath.recipes_example_recipe.comment`,`线性强化配方示例，展示星级阶段、材料、锻印、失败处理和 source_ids 精确绑定。`],[`emakistrengthen.filePath.recipes_example_branch_recipe.title`,`示例分支配方`],[`emakistrengthen.filePath.recipes_example_branch_recipe.comment`,`分支强化配方示例，展示路线选择、子分支、阶段继承和演示用 source_ids 绑定。`],[`emakistrengthen.filePath.gui_strengthen_gui.title`,`强化 GUI`],[`emakistrengthen.filePath.gui_strengthen_gui.comment`,`强化界面 GUI 模板，控制目标物品、材料和确认槽位。`],[`emakistrengthen.file.plugin.title`,`插件描述`],[`emakistrengthen.file.plugin.comment`,`plugin.yml 元数据、命令、权限和依赖声明。`],[`emakistrengthen.file.web-console.title`,`WebUIEdit 注册`],[`emakistrengthen.file.web-console.comment`,`此插件暴露给 WebUIEdit 的文件分组、编辑器类型和前端扩展入口。`],...I.flatMap(([e,t,n])=>[[`emakistrengthen.field.${e}`,t],[`emakistrengthen.comment.${e}`,n]]),...L.flatMap(([e,t,n])=>[[`emakistrengthen.field.${e}`,t],[`emakistrengthen.comment.${e}`,n]])]),z=(0,e.defineSchemaAst)({id:`emakistrengthen-branch-tree`,moduleId:w,pathPrefix:`recipes/`,fields:[(0,e.objectField)({path:`branch_tree`,label:T(`分支树`,`Branch tree`),comment:T(`分支强化路线配置。`,`Branching strengthen route configuration.`),fields:[(0,e.textField)({path:`branch_id`,label:T(`分支 ID`,`Branch id`),comment:T(`分支唯一标识，root 节点可使用 root。`,`Unique branch id; root may use root.`)}),(0,e.textField)({path:`display_name`,label:T(`显示名称`,`Display name`),comment:T(`分支在 GUI 或提示中的显示名称。`,`Display name used in GUI or prompts.`)}),(0,e.numberField)({path:`fork_after_star`,label:T(`分叉星级`,`Fork after star`),comment:T(`-1 表示不再分叉。`,`-1 means no further fork.`),defaultValue:-1}),(0,e.objectMapField)({path:`stars`,label:T(`星级阶段`,`Star stages`),comment:T(`该分支内的星级阶段。`,`Star stages inside this branch.`),creatableChildren:!0}),(0,e.objectMapField)({path:`children`,label:T(`子分支`,`Child branches`),comment:T(`此分支后续可选择的子路线。`,`Selectable child routes after this branch.`),creatableChildren:!0})]}),(0,e.objectMapField)({path:`stars`,label:T(`星级阶段`,`Star stages`),comment:T(`线性配方的星级阶段。`,`Star stages for linear recipes.`),creatableChildren:!0}),(0,e.materialCostListField)({path:`materials`,label:T(`材料`,`Materials`),comment:T(`强化材料列表。`,`Strengthen material list.`)}),(0,e.currencyCostListField)({path:`economy.currencies`,label:T(`货币消耗`,`Currency costs`),comment:T(`强化经济消耗中的货币列表。`,`Currency list used by strengthen economy costs.`)}),(0,e.effectListField)({path:`effects`,label:T(`效果`,`Effects`),comment:T(`强化阶段效果列表。`,`Strengthen stage effects.`)}),(0,e.conditionGroupField)({path:`condition`,label:T(`强化条件`,`Strengthen condition`),comment:T(`执行强化前检查的 CoreLib 条件组。`,`CoreLib condition group checked before strengthening.`)}),(0,e.nameLoreActionField)({path:`name_actions`,label:T(`名称动作`,`Name actions`),comment:T(`强化成功后对物品显示名称执行的动作。`,`Actions applied to item display name after success.`)}),(0,e.nameLoreActionField)({path:`lore_actions`,label:T(`Lore 动作`,`Lore actions`),comment:T(`强化成功后对物品 Lore 执行的动作。`,`Actions applied to item lore after success.`)})]}),B=(0,e.defineConfigSchema)(z),V={metaFields:I.map(([e,t,n,r])=>[e,t,n,r,e===`success_rates`?{creatableChildren:!0}:void 0]),fileSchemas:[{pathPrefix:`recipes/`,fields:L}],createTemplates:[[`success_rates`,N],[`stars`,P],[`branch_tree.stars`,P],[`branch_tree.children`,F]],listItemSchemas:[],rules:[[{key:`effects`},{label:`效果`,comment:`强化阶段效果列表，按 type 分流为变量、EA 属性、ES 技能、名称/Lore 动作。`,type:`effects`}],[{key:`variables`},{label:`变量`,comment:`变量键值；属性计算应保持数值结果，name/lore actions 文本模板可使用随机文本、随机字符、权重随机字符和条件字符。`,type:`variablesMap`}],[{key:`ea_attributes`},{label:`EA 属性`,comment:`EmakiAttribute 属性数值映射。`,type:`dynamic_map`}],[{key:`es_skills`},{label:`ES 技能`,comment:`EmakiSkills 技能 ID 列表。`,type:`stringList`}],[{key:`name_actions`},{label:`名称动作链`,comment:`对物品显示名称执行的 CoreLib Action 列表。`,type:`actions`}],[{key:`lore_actions`},{label:`Lore 动作链`,comment:`对物品 Lore 执行的 CoreLib Action 列表。`,type:`actions`}],[{key:`stars`},{label:`星级阶段`,comment:`按目标星级添加阶段配置。每个子键应为星级数字。`,type:`object`,creatableChildren:!0,createTemplates:[P]}],[{key:`children`},{label:`子分支`,comment:`按分支 ID 添加后续路线，用于分支强化选择。`,type:`object`,creatableChildren:!0,createTemplates:[F]}]],listItemSchemaRules:[[{key:`materials`},k],[{key:`currencies`},A]]},H={...R,"emakistrengthen.surface.gui":`强化 GUI`,"emakistrengthen.field.target_item":`目标物品`,"emakistrengthen.field.material":`强化材料`,"emakistrengthen.field.confirm":`确认按钮`},U=`EmakiStrengthen`,W=e.localeText,G=(0,e.defineEmakiPluginWebModule)({module:{id:U,displayName:`Strengthen`,summaryKey:`emakistrengthen.module.summary`,icon:`strengthen`,tone:`strengthen`},files:[{id:`config`,path:`config.yml`,kind:`CONFIG`,titleKey:`emakistrengthen.file.config.title`,commentKey:`emakistrengthen.file.config.comment`},{id:`recipes`,path:`recipes/**/*.yml`,kind:`CONFIG`,titleKey:`emakistrengthen.file.recipes.title`,commentKey:`emakistrengthen.file.recipes.comment`},{id:`gui`,path:`gui/**/*.yml`,kind:`GUI`,editorId:`emakistrengthen:gui`,titleKey:`emakistrengthen.file.gui.title`,commentKey:`emakistrengthen.file.gui.comment`}],schemas:[B],config:V,effectTypes:O,previews:[(0,e.definePreview)({kind:`CONFIG`,pathPattern:`recipes/**/*.yml`,component:f,label:W(`强化路线蓝图`,`Strengthen route blueprint`),priority:20})],guiEditors:[(0,e.defineGuiEditor)({editorId:`emakistrengthen:gui`,label:W(`强化 GUI`,`Strengthen GUI`),fields:[[`type`,`槽位类型`,`强化业务槽位语义。可选预设值，材料输入槽可用 material_input_0/1/2… 自定义。`,`enum`,{options:[`target_item`,`preview_display`,`temper_display`,`confirm`,`material_input_0`,`material_input_1`,`material_input_2`],optionLabelPrefix:`slotType`}],[`target_item`,`目标物品`,`放入待强化物品的槽位。`,`text`],[`material`,`强化材料`,`放入强化材料的槽位。`,`text`],[`success_preview`,`成功率预览`,`显示当前强化成功率与目标星级的槽位。`,`text`],[`confirm`,`确认按钮`,`执行强化操作的按钮槽位。`,`text`]]})],insightDefinitions:[{pathPrefix:`recipes/`,idType:`strengthen_recipe`,idPath:`id`}],pluginApiRoutes:[{id:`route-preview`,route:`strengthen/route-preview`,readonly:!0,description:`Strengthen route preview API.`}],locales:[(0,e.defineLocales)(`zh-CN`,H),(0,e.defineLocales)(`en-US`,C)],capabilities:(0,e.defineCapabilities)([`config`,`gui`,`preview`,`insight`,`pluginApi`,`diagnostics`]),diagnostics:[{id:`emakistrengthen.manifest-v2`,description:`Strengthen registers config, preview, and GUI editors through Manifest v2.`,severity:`info`},{id:`emakistrengthen.branch-tree-schema`,description:`Strengthen branch tree, stars, materials, and economy metadata are represented in Schema AST metadata.`,severity:`info`}]}),K=!1;function q(){K||(K=!0,u(),(0,e.registerEmakiPluginWebModule)(G))}q()})(EmakiWebConsole,React);