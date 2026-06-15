(function(e,t){var n=Object.create,r=Object.defineProperty,i=Object.getOwnPropertyDescriptor,a=Object.getOwnPropertyNames,o=Object.getPrototypeOf,s=Object.prototype.hasOwnProperty,c=(e,t,n,o)=>{if(t&&typeof t==`object`||typeof t==`function`)for(var c=a(t),l=0,u=c.length,d;l<u;l++)d=c[l],!s.call(e,d)&&d!==n&&r(e,d,{get:(e=>t[e]).bind(null,d),enumerable:!(o=i(t,d))||o.enumerable});return e};t=((e,t,i)=>(i=e==null?{}:n(o(e)),c(t||!e||!e.__esModule?r(i,`default`,{value:e,enumerable:!0}):i,e)))(t,1);var l=`.level-curve-preview {
  margin-top: 16px;
  display: grid;
  gap: 12px;
  padding: 14px;
  color: var(--text);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}

.level-curve-preview__header,
.level-curve-preview__summary,
.level-curve-preview__legend,
.level-curve-preview__inspector-head,
.level-curve-preview__tooltip-title {
  display: flex;
  gap: 8px;
}

.level-curve-preview__header {
  justify-content: space-between;
  align-items: flex-start;
}

.level-curve-preview__title {
  margin: 0;
  font-size: 16px;
  line-height: 1.35;
  letter-spacing: -0.01em;
}

.level-curve-preview__hint,
.level-curve-preview__empty,
.level-curve-preview__inspector-empty {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.45;
}

.level-curve-preview__controls {
  display: grid;
  grid-template-columns: minmax(220px, 2fr) repeat(3, minmax(120px, 1fr));
  gap: 10px;
}

.level-curve-preview__field {
  display: grid;
  gap: 5px;
  color: var(--muted);
  font-size: 12px;
}

.level-curve-preview__field input,
.level-curve-preview__field select {
  min-height: 32px;
  color: var(--text);
  background: var(--input);
  border: 1px solid var(--line-2);
  border-radius: 6px;
  padding: 6px 8px;
}

.level-curve-preview__field input:focus-visible,
.level-curve-preview__field select:focus-visible,
.level-curve-preview__legend-button:focus-visible,
.level-curve-preview__table-head:focus-visible {
  outline: 1px solid var(--accent);
  outline-offset: 2px;
}

.level-curve-preview__legend {
  flex-wrap: wrap;
}

.level-curve-preview__legend-button {
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  color: var(--text);
  background: color-mix(in oklch, var(--surface-2) 78%, transparent);
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 11px;
  cursor: pointer;
}

.level-curve-preview__legend-button[aria-pressed="false"] {
  opacity: .48;
}

.level-curve-preview__swatch {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  flex: 0 0 auto;
  background: var(--curve-color, var(--accent));
}

.level-curve-preview__chart-wrap {
  overflow-x: auto;
}

.level-curve-preview__chart {
  display: block;
  width: 100%;
  min-width: 720px;
  background: var(--input);
  border: 1px solid var(--line);
  border-radius: 6px;
}

.level-curve-preview__summary {
  align-items: center;
  flex-wrap: wrap;
  color: var(--muted);
  font-size: 12px;
}

.level-curve-preview__summary strong {
  color: var(--text);
}

.level-curve-preview__inspector,
.level-curve-preview__inspector-empty {
  padding: 10px;
  border: 1px solid var(--line-2);
  border-radius: 8px;
  background: color-mix(in oklch, var(--surface-2) 70%, transparent);
}

.level-curve-preview__inspector-head {
  justify-content: space-between;
  align-items: center;
  color: var(--text);
  font-size: 12px;
}

.level-curve-preview__inspector-rows {
  max-height: 220px;
  overflow-y: auto;
  display: grid;
  gap: 8px;
  margin-top: 8px;
  padding-right: 2px;
  scrollbar-width: thin;
}

.level-curve-preview__tooltip-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
}

.level-curve-preview__tooltip-copy {
  min-width: 0;
}

.level-curve-preview__tooltip-title {
  align-items: baseline;
  min-width: 0;
  font-size: 12px;
  font-weight: 700;
}

.level-curve-preview__tooltip-meta {
  margin-top: 3px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.35;
}

.level-curve-preview__tooltip-number {
  display: block;
  margin-top: 1px;
  color: var(--accent-strong);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.level-curve-preview__tables {
  display: grid;
  gap: 8px;
}

.level-curve-preview__table-group {
  border: 1px solid var(--line);
  border-radius: 8px;
  overflow: hidden;
  background: color-mix(in oklch, var(--surface-2) 54%, transparent);
}

.level-curve-preview__table-group[aria-disabled="true"] {
  opacity: .5;
}

.level-curve-preview__table-head {
  width: 100%;
  min-height: 36px;
  display: grid;
  grid-template-columns: 18px 10px minmax(120px, 1fr) minmax(80px, .6fr) auto auto;
  align-items: center;
  justify-items: center;
  gap: 8px;
  padding: 7px 10px;
  color: var(--text);
  background: transparent;
  text-align: center;
  border: 0;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
}

.level-curve-preview__warning-pill {
  color: var(--amber);
  border: 1px solid color-mix(in oklch, var(--amber) 44%, var(--line) 56%);
  border-radius: 999px;
  padding: 1px 6px;
}

.level-curve-preview__table-scroll {
  overflow-x: auto;
}

.level-curve-preview__table {
  width: 100%;
  min-width: 720px;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 12px;
  color: var(--text);
}

.level-curve-preview__table th,
.level-curve-preview__table td {
  padding: 7px 10px;
  border-bottom: 1px solid color-mix(in oklch, var(--line) 72%, transparent);
  vertical-align: middle;
  text-align: center;
}

.level-curve-preview__table th {
  color: var(--muted);
  font-weight: 700;
}

.level-curve-preview__col-lv {
  width: 64px;
}

.level-curve-preview__col-required {
  width: 120px;
}

.level-curve-preview__col-total {
  width: 130px;
}

.level-curve-preview__col-growth {
  width: 100px;
}

.level-curve-preview__col-warnings {
  width: 120px;
}

.level-curve-preview__num {
  font-variant-numeric: tabular-nums;
}

.level-curve-preview__source {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--subtle-readable);
}

.level-curve-preview__diagnostic {
  margin-top: 8px;
  color: var(--subtle-readable);
  font-size: 11px;
}

.level-curve-preview__diagnostic pre {
  margin: 6px 0 0;
  white-space: pre-wrap;
}

@media (max-width: 920px) {
  .level-curve-preview__header,
  .level-curve-preview__controls {
    display: grid;
    grid-template-columns: 1fr;
  }

  .level-curve-preview__chart-wrap,
  .level-curve-preview__table-scroll {
    margin-inline: -2px;
    padding-bottom: 4px;
  }

  .level-curve-preview__legend-button,
  .level-curve-preview__table-head,
  .level-curve-preview__field input,
  .level-curve-preview__field select {
    min-height: 44px;
  }

  .level-curve-preview__tooltip-meta {
    grid-template-columns: 1fr;
  }

  .level-curve-preview__table {
    min-width: 640px;
  }
}
`;function u(){(0,e.injectExtensionStyles)(`emakilevel-curve-preview`,l)}var d=e.localeText,f=[[`version`,d(`配置版本`,`Config version`),d(`默认配置结构版本。`,`Default config schema version.`),`text`],[`language`,d(`语言`,`Language`),d(`语言文件 ID，对应 lang/<language>.yml。`,`Language file id under lang/<language>.yml.`),`text`],[`release_default_data`,d(`释放默认数据`,`Release default data`),d(`缺失默认 types/sources/gui 时是否释放内置文件。`,`Whether bundled types/sources/gui files should be released when missing.`),`boolean`],[`primary_type`,d(`主等级类型`,`Primary type`),d(`默认展示和 Action 未指定类型时使用的等级类型。`,`Default level type for display and actions without type.`),`text`],[`level.default_start_level`,d(`默认起始等级`,`Default start level`),d(`类型未配置 start_level 时使用。`,`Used when a type does not define start_level.`),`number`],[`level.default_max_level`,d(`默认最高等级`,`Default max level`),d(`类型未配置 max_level 时使用。`,`Used when a type does not define max_level.`),`number`],[`level.max_auto_upgrade_steps`,d(`自动升级步数`,`Auto upgrade steps`),d(`一次经验变更最多连升多少级。`,`Maximum auto-upgrade steps in one exp operation.`),`number`],[`pdc.enabled`,d(`同步 PDC`,`Sync PDC`),d(`是否向玩家实体写入等级数据。`,`Whether to write level data to player PDC.`),`boolean`],[`pdc.namespace`,d(`PDC 命名空间`,`PDC namespace`),d(`等级 PDC 使用的命名空间。`,`Namespace used by level PDC keys.`),`text`],[`attribute.enabled`,d(`属性桥接`,`Attribute bridge`),d(`是否向 EmakiAttribute 贡献等级属性。`,`Whether levels contribute EmakiAttribute attributes.`),`boolean`],[`attribute.provider_id`,d(`属性来源 ID`,`Attribute provider id`),d(`注册到 EmakiAttribute 的 Provider ID。`,`Provider id registered into EmakiAttribute.`),`text`],[`mythicmobs.enabled`,d(`MythicMobs 接入`,`MythicMobs integration`),d(`是否启用 MythicMobs 击杀和 Drop 接入。`,`Whether MythicMobs kill and drop integration is enabled.`),`boolean`],[`mythicmobs.drops.enabled`,d(`Mythic Drops`,`Mythic drops`),d(`是否注册 emakilevel_exp/elv_exp 自定义 Drop。`,`Whether to register emakilevel_exp/elv_exp custom drops.`),`boolean`],[`mythicmobs.drops.names`,d(`Drop 名称`,`Drop names`),d(`可识别的 MythicMobs 自定义 Drop 名称。`,`Recognized MythicMobs custom drop names.`),`stringList`],[`anti_abuse.placed_block_tracking`,d(`记录放置方块`,`Track placed blocks`),d(`记录玩家放置方块用于破坏经验防刷。`,`Track player-placed blocks for break-exp anti-abuse.`),`boolean`],[`anti_abuse.placed_block_exp`,d(`放置方块给经验`,`Placed blocks grant exp`),d(`关闭后玩家放置再破坏的方块不给经验。`,`When false, player-placed blocks do not grant break exp.`),`boolean`]],p=[[`id`,d(`类型 ID`,`Type id`),d(`等级类型唯一 ID，例如 combat。`,`Unique level type id, e.g. combat.`),`text`],[`enabled`,d(`启用`,`Enabled`),d(`是否启用该等级类型。`,`Whether this level type is enabled.`),`boolean`],[`display_name`,d(`显示名称`,`Display name`),d(`MiniMessage 显示名。`,`MiniMessage display name.`),`text`],[`description`,d(`描述`,`Description`),d(`等级类型描述。`,`Level type description.`),`stringList`],[`primary`,d(`主等级`,`Primary`),d(`是否作为主等级。`,`Whether this is the primary type.`),`boolean`],[`start_level`,d(`起始等级`,`Start level`),d(`玩家初始等级。`,`Initial player level.`),`number`],[`max_level`,d(`最高等级`,`Max level`),d(`玩家可达到的最高等级。`,`Maximum reachable level.`),`number`],[`requirement.group`,d(`需求分组`,`Requirement group`),d(`requirements.yml 中的需求分组。`,`Requirement group in requirements.yml.`),`text`],[`requirement.formula`,d(`类型公式`,`Type formula`),d(`覆盖分组/全局公式的类型专属公式。`,`Type-specific formula overriding group/global formula.`),`text`],[`upgrade.enabled`,d(`允许升级`,`Upgrade enabled`),d(`是否允许该类型升级。`,`Whether this type can level up.`),`boolean`],[`upgrade.auto_upgrade`,d(`自动升级`,`Auto upgrade`),d(`获得经验后是否自动升级。`,`Whether exp gain triggers auto-upgrade.`),`boolean`],[`upgrade.manual_upgrade`,d(`手动升级`,`Manual upgrade`),d(`是否允许 /elv levelup。`,`Whether /elv levelup is allowed.`),`boolean`],[`upgrade.actions.gain`,d(`获得经验动作`,`Exp gain actions`),d(`获得经验后执行的 CoreLib Action。`,`CoreLib actions executed after exp gain.`),`stringList`],[`upgrade.actions.success`,d(`升级成功动作`,`Success actions`),d(`升级成功后执行的 CoreLib Action。`,`CoreLib actions executed after successful level-up.`),`stringList`],[`upgrade.actions.failure`,d(`升级失败动作`,`Failure actions`),d(`升级失败后执行的 CoreLib Action。`,`CoreLib actions executed after failed level-up.`),`stringList`],[`attributes.enabled`,d(`启用属性`,`Attributes enabled`),d(`是否将此类型等级转为属性贡献。`,`Whether this type contributes attributes.`),`boolean`],[`attributes.values`,d(`属性公式`,`Attribute formulas`),d(`属性 ID 到表达式的映射。`,`Map of attribute id to expression.`),`object`,{creatableChildren:!0}]],m=[[`id`,d(`来源文件 ID`,`Source file id`),d(`来源配置文件标识。`,`Source config file id.`),`text`],[`enabled`,d(`启用`,`Enabled`),d(`是否启用此来源文件。`,`Whether this source file is enabled.`),`boolean`],[`sources`,d(`经验来源`,`Experience sources`),d(`按来源 ID 配置的经验规则。`,`Experience source rules keyed by source id.`),`object`,{creatableChildren:!0}]],h={enabled:[d(`启用`,`Enabled`),d(`是否启用该节点。`,`Whether this node is enabled.`),`boolean`],type:[d(`等级类型`,`Level type`),d(`经验写入的等级类型 ID。`,`Target level type id.`),`text`],trigger:[d(`触发器`,`Trigger`),d(`经验来源触发器。`,`Experience source trigger.`),`enum`,{options:[`entity_kill`,`mythic_mob_kill`,`block_break`,`crop_harvest`,`player_fish`,`craft_item`,`brew_complete`,`furnace_extract`,`entity_tame`],optionLabelPrefix:`trigger`}],exp_formula:[d(`经验公式`,`Exp formula`),d(`使用 %变量% 计算本次经验。`,`Formula using %variables% to calculate exp.`),`text`],blocks:[d(`方块`,`Blocks`),d(`匹配 Bukkit Material 名称。`,`Matching Bukkit material names.`),`stringList`],entity_types:[d(`实体类型`,`Entity types`),d(`匹配 Bukkit EntityType 名称。`,`Matching Bukkit EntityType names.`),`stringList`],mob_ids:[d(`MythicMob ID`,`MythicMob IDs`),d(`匹配 MythicMobs 内部 ID。`,`Matching MythicMobs internal ids.`),`stringList`],result_item_sources:[d(`结果物品源`,`Result item sources`),d(`匹配 CoreLib ItemSource。`,`Matching CoreLib ItemSource values.`),`stringList`],states:[d(`状态`,`States`),d(`事件状态名称。`,`Event state names.`),`stringList`],potion_types:[d(`药水类型`,`Potion types`),d(`药水类型名称。`,`Potion type names.`),`stringList`]};function g({api:n,file:r,data:i}){let a=typeof i?.id==`string`?i.id:``,[o,s]=(0,t.useState)(r.path.startsWith(`types/`)?a:``),[c,l]=(0,t.useState)(1),[u,f]=(0,t.useState)(80),[p,m]=(0,t.useState)(`requiredExp`),[h,g]=(0,t.useState)(null),[S,C]=(0,t.useState)(!1),[E,D]=(0,t.useState)(``),[O,k]=(0,t.useState)(new Set),[A,j]=(0,t.useState)(new Set),[M,N]=(0,t.useState)(null),P=(0,t.useMemo)(()=>o.split(`,`).map(e=>e.trim()).filter(Boolean),[o]),F=h?.curves??[],I=F.filter(e=>!O.has(e.type)),L=async()=>{C(!0),D(``);try{let e=await n.pluginApi(`level`,`curve`,{types:P,fromLevel:c,toLevel:u}),t=Array.isArray(e.curves)?e.curves:[];g({curves:t,limits:e.limits,warnings:e.warnings}),k(new Set),j(new Set(t[0]?.type?[t[0].type]:[])),N(null)}catch(e){D(e instanceof Error?e.message:String(e))}finally{C(!1)}};(0,t.useEffect)(()=>{L()},[]);let R=e=>k(t=>{let n=new Set(t);return n.has(e)?n.delete(e):n.add(e),n}),z=e=>j(t=>{let n=new Set(t);return n.has(e)?n.delete(e):n.add(e),n}),B=()=>{if(!F.length)return;let e=[`type,target_level,required_exp,total_exp,growth_rate,source,warnings`];F.forEach(t=>t.points.forEach(n=>e.push([t.type,n.targetLevel,n.requiredExp,n.totalExp,n.growthRate,n.source,(n.warnings??[]).map(e=>e.type).join(`|`)].map(T).join(`,`))));let t=new Blob([e.join(`
`)],{type:`text/csv;charset=utf-8`}),n=URL.createObjectURL(t),r=document.createElement(`a`);r.href=n,r.download=`emakilevel-curve.csv`,r.click(),URL.revokeObjectURL(n)},V=M==null?[]:I.map((e,t)=>({curve:e,point:e.points.find(e=>e.targetLevel===M)??null,color:w(e,F,t)})).filter(e=>e.point);return t.default.createElement(`section`,{className:`level-curve-preview`},t.default.createElement(`div`,{className:`level-curve-preview__header`},t.default.createElement(`div`,null,t.default.createElement(`h3`,{className:`level-curve-preview__title`},d(`等级曲线`,`Level curve`)),t.default.createElement(`p`,{className:`level-curve-preview__hint`},d(`由服务端基于真实 RequirementService 计算，前端只负责展示。结构化配置仍可继续编辑，预览只反映运行时计算结果。`,`Calculated by the server through the real RequirementService; the frontend only visualizes it. Structured config remains editable; the preview reflects runtime calculation only.`))),t.default.createElement(e.Button,{size:`sm`,onClick:L,disabled:S},S?d(`加载中...`,`Loading...`):d(`刷新曲线`,`Refresh curve`))),t.default.createElement(`div`,{className:`level-curve-preview__controls`},t.default.createElement(`label`,{className:`level-curve-preview__field`},d(`等级类型，逗号分隔`,`Level types, comma separated`),t.default.createElement(`input`,{value:o,onChange:e=>s(e.target.value),placeholder:d(`留空显示全部启用类型`,`Empty = all enabled types`)})),t.default.createElement(`label`,{className:`level-curve-preview__field`},d(`起始目标等级`,`From target level`),t.default.createElement(`input`,{type:`number`,min:1,value:c,onChange:e=>l(Number(e.target.value)||1)})),t.default.createElement(`label`,{className:`level-curve-preview__field`},d(`结束目标等级`,`To target level`),t.default.createElement(`input`,{type:`number`,min:1,value:u,onChange:e=>f(Number(e.target.value)||1)})),t.default.createElement(`label`,{className:`level-curve-preview__field`},d(`图表指标`,`Metric`),t.default.createElement(`select`,{value:p,onChange:e=>m(e.target.value)},t.default.createElement(`option`,{value:`requiredExp`},d(`单级需求经验`,`Required exp`)),t.default.createElement(`option`,{value:`totalExp`},d(`累计总经验`,`Total exp`)),t.default.createElement(`option`,{value:`growthRate`},d(`增长率`,`Growth rate`))))),E?t.default.createElement(_,{route:`curve`,message:E}):null,F.length?t.default.createElement(t.default.Fragment,null,t.default.createElement(v,{curves:F,hiddenTypes:O,onToggle:R}),t.default.createElement(`div`,{className:`level-curve-preview__chart-wrap`},t.default.createElement(y,{curves:F,visibleCurves:I,hiddenTypes:O,metric:p,hoverLevel:M,onHover:N})),t.default.createElement(b,{level:M,rows:V}),t.default.createElement(`div`,{className:`level-curve-preview__summary`},t.default.createElement(`span`,null,d(`曲线数量`,`Curves`),`: `,t.default.createElement(`strong`,null,F.length)),t.default.createElement(`span`,null,d(`显示中`,`Visible`),`: `,t.default.createElement(`strong`,null,I.length)),t.default.createElement(`span`,null,d(`单类型最多点数`,`Max points/type`),`: `,t.default.createElement(`strong`,null,h?.limits?.maxPointsPerType??`-`)),t.default.createElement(e.Button,{size:`sm`,variant:`soft`,onClick:B},d(`导出 CSV`,`Export CSV`))),t.default.createElement(x,{curves:F,hiddenTypes:O,openTypes:A,onToggleOpen:z})):t.default.createElement(`div`,{className:`level-curve-preview__empty`},d(`暂无曲线数据。`,`No curve data.`)))}function _({route:n,message:r}){return t.default.createElement(e.InlineError,null,t.default.createElement(`strong`,null,d(`等级曲线预览暂不可用。`,`Level curve preview is unavailable.`)),t.default.createElement(`p`,null,d(`插件 API level/${n} 请求失败；当前 YAML 仍可继续编辑，保存不依赖此只读预览。`,`Plugin API level/${n} failed. The current YAML remains editable; saving does not depend on this read-only preview.`)),t.default.createElement(`details`,{className:`level-curve-preview__diagnostic`},t.default.createElement(`summary`,null,d(`开发者诊断`,`Developer diagnostics`)),t.default.createElement(`pre`,null,r)))}function v({curves:e,hiddenTypes:n,onToggle:r}){return t.default.createElement(`div`,{className:`level-curve-preview__legend`},e.map((i,a)=>{let o=w(i,e,a),s=n.has(i.type);return t.default.createElement(`button`,{key:i.type,type:`button`,className:`level-curve-preview__legend-button`,onClick:()=>r(i.type),style:k(o),"aria-pressed":!s},t.default.createElement(`span`,{className:`level-curve-preview__swatch`}),t.default.createElement(`code`,null,i.type),t.default.createElement(`small`,null,i.points.length))}))}function y({curves:e,visibleCurves:n,hiddenTypes:r,metric:i,hoverLevel:a,onHover:o}){let s={left:42,right:28,top:26,bottom:34},c=n.flatMap(e=>e.points.map(t=>({curve:e,point:t,value:C(t,i)}))),l=c.length?Math.min(...c.map(e=>e.point.targetLevel)):1,u=c.length?Math.max(...c.map(e=>e.point.targetLevel)):1,f=Math.max(1,...c.map(e=>e.value)),p=820-s.left-s.right,m=300-s.top-s.bottom,h=e=>s.left+(e-l)/Math.max(1,u-l)*p,g=e=>s.top+m-e/f*m,_=Array.from(new Set(c.map(e=>e.point.targetLevel))).sort((e,t)=>e-t),v=(e,t)=>{let n=l+((e-t.left)/t.width*820-s.left)/Math.max(1,p)*Math.max(1,u-l);return _.reduce((e,t)=>Math.abs(t-n)<Math.abs(e-n)?t:e,_[0]??l)};return t.default.createElement(`svg`,{viewBox:`0 0 820 300`,className:`level-curve-preview__chart`,role:`img`,"aria-label":d(`等级曲线图`,`Level curve chart`),onMouseMove:e=>{if(!_.length)return;let t=e.currentTarget.getBoundingClientRect(),n=v(e.clientX,t);n!==a&&o(n)}},[0,.25,.5,.75,1].map(e=>t.default.createElement(`line`,{key:e,x1:s.left,x2:820-s.right,y1:s.top+m*e,y2:s.top+m*e,stroke:`color-mix(in oklch, var(--line) 58%, transparent)`})),t.default.createElement(`line`,{x1:s.left,y1:300-s.bottom,x2:820-s.right,y2:300-s.bottom,stroke:`color-mix(in oklch, var(--line-2) 78%, transparent)`}),t.default.createElement(`line`,{x1:s.left,y1:s.top,x2:s.left,y2:300-s.bottom,stroke:`color-mix(in oklch, var(--line-2) 78%, transparent)`}),a!=null&&t.default.createElement(`line`,{x1:h(a),y1:s.top,x2:h(a),y2:300-s.bottom,stroke:`color-mix(in oklch, var(--text) 52%, transparent)`,strokeDasharray:`4 4`}),e.map((n,o)=>{if(r.has(n.type))return null;let s=w(n,e,o),c=n.points.map((e,t)=>`${t===0?`M`:`L`} ${h(e.targetLevel)} ${g(C(e,i))}`).join(` `);return t.default.createElement(`g`,{key:n.type},t.default.createElement(`path`,{d:c,fill:`none`,stroke:s,strokeWidth:`3`,strokeLinecap:`round`,strokeLinejoin:`round`}),n.points.map(e=>{let r=a===e.targetLevel,o=(e.warnings?.length??0)>0;return t.default.createElement(`circle`,{key:`${n.type}-${e.targetLevel}`,cx:h(e.targetLevel),cy:g(C(e,i)),r:r?5:o?4:2.6,fill:o?`var(--amber)`:s,stroke:r?`var(--surface)`:`transparent`,strokeWidth:r?2:0})}))}),t.default.createElement(`text`,{x:s.left,y:292,fill:`var(--muted)`,fontSize:`11`},`Lv.`,l),t.default.createElement(`text`,{x:820-s.right,y:292,fill:`var(--muted)`,fontSize:`11`,textAnchor:`end`},`Lv.`,u))}function b({level:e,rows:n}){return e==null||!n.length?t.default.createElement(`div`,{className:`level-curve-preview__inspector-empty`},d(`移动到曲线上的等级位置查看各等级组数值。`,`Move over a level on the chart to inspect every visible group.`)):t.default.createElement(`aside`,{className:`level-curve-preview__inspector`,"aria-live":`polite`},t.default.createElement(`div`,{className:`level-curve-preview__inspector-head`},t.default.createElement(`strong`,null,d(`目标等级`,`Target level`),` Lv.`,e),t.default.createElement(`span`,null,n.length,` `,d(`组`,`groups`))),t.default.createElement(`div`,{className:`level-curve-preview__inspector-rows`},n.map(({curve:e,point:n,color:r})=>t.default.createElement(`div`,{key:e.type,className:`level-curve-preview__tooltip-row`,style:k(r)},t.default.createElement(`span`,{className:`level-curve-preview__swatch`}),t.default.createElement(`div`,{className:`level-curve-preview__tooltip-copy`},t.default.createElement(`div`,{className:`level-curve-preview__tooltip-title`},t.default.createElement(`code`,null,e.type),(n.warnings?.length??0)>0?t.default.createElement(`small`,{className:`level-curve-preview__warning-pill`},n.warnings?.length):null),t.default.createElement(`div`,{className:`level-curve-preview__tooltip-meta`},t.default.createElement(`span`,null,d(`需求`,`Required`),` `,t.default.createElement(`b`,{className:`level-curve-preview__tooltip-number`},E(n.requiredExp))),t.default.createElement(`span`,null,d(`累计`,`Total`),` `,t.default.createElement(`b`,{className:`level-curve-preview__tooltip-number`},E(n.totalExp))),t.default.createElement(`span`,null,d(`增长率`,`Growth`),` `,t.default.createElement(`b`,{className:`level-curve-preview__tooltip-number`},D(n.growthRate)))))))))}function x({curves:e,hiddenTypes:n,openTypes:r,onToggleOpen:i}){return t.default.createElement(`div`,{className:`level-curve-preview__tables`},e.map((a,o)=>{let s=r.has(a.type),c=n.has(a.type),l=a.points.reduce((e,t)=>e+(t.warnings?.length??0),0)+(a.warnings?.length??0),u=w(a,e,o);return t.default.createElement(`section`,{key:a.type,className:`level-curve-preview__table-group`,"aria-disabled":c},t.default.createElement(`button`,{type:`button`,className:`level-curve-preview__table-head`,onClick:()=>i(a.type),"aria-expanded":s,style:k(u)},t.default.createElement(`span`,null,s?`⌄`:`›`),t.default.createElement(`span`,{className:`level-curve-preview__swatch`}),t.default.createElement(`code`,null,a.type),t.default.createElement(`em`,null,a.fromLevel,`-`,a.toLevel),t.default.createElement(`small`,null,a.points.length,` `,d(`点`,`points`)),l?t.default.createElement(`small`,{className:`level-curve-preview__warning-pill`},l):null),s&&t.default.createElement(S,{curve:a}))}))}function S({curve:e}){return t.default.createElement(`div`,{className:`level-curve-preview__table-scroll`},t.default.createElement(`table`,{className:`level-curve-preview__table`},t.default.createElement(`colgroup`,null,t.default.createElement(`col`,{className:`level-curve-preview__col-lv`}),t.default.createElement(`col`,{className:`level-curve-preview__col-required`}),t.default.createElement(`col`,{className:`level-curve-preview__col-total`}),t.default.createElement(`col`,{className:`level-curve-preview__col-growth`}),t.default.createElement(`col`,null),t.default.createElement(`col`,{className:`level-curve-preview__col-warnings`})),t.default.createElement(`thead`,null,t.default.createElement(`tr`,null,t.default.createElement(`th`,{className:`level-curve-preview__num`},`Lv`),t.default.createElement(`th`,{className:`level-curve-preview__num`},d(`需求`,`Required`)),t.default.createElement(`th`,{className:`level-curve-preview__num`},d(`累计`,`Total`)),t.default.createElement(`th`,{className:`level-curve-preview__num`},d(`增长率`,`Growth`)),t.default.createElement(`th`,null,d(`来源`,`Source`)),t.default.createElement(`th`,null,d(`警告`,`Warnings`)))),t.default.createElement(`tbody`,null,e.points.map(e=>t.default.createElement(`tr`,{key:e.targetLevel},t.default.createElement(`td`,{className:`level-curve-preview__num`},e.targetLevel),t.default.createElement(`td`,{className:`level-curve-preview__num`},E(e.requiredExp)),t.default.createElement(`td`,{className:`level-curve-preview__num`},E(e.totalExp)),t.default.createElement(`td`,{className:`level-curve-preview__num`},D(e.growthRate)),t.default.createElement(`td`,null,t.default.createElement(`code`,{className:`level-curve-preview__source`},e.source)),t.default.createElement(`td`,null,(e.warnings??[]).map(e=>e.type).join(`, `)))))))}function C(e,t){return t===`totalExp`?e.totalExp:t===`growthRate`?Math.max(0,e.growthRate*100):e.requiredExp}function w(e,t,n){let r=Math.max(0,t.findIndex(t=>t.type===e.type));return O[(r>=0?r:n)%O.length]}function T(e){let t=String(e??``);return/[",\n]/.test(t)?`"${t.replace(/"/g,`""`)}"`:t}function E(e){return Number.isFinite(e)?e.toLocaleString(void 0,{maximumFractionDigits:2}):`-`}function D(e){return Number.isFinite(e)?`${(e*100).toFixed(2)}%`:`-`}var O=[`var(--viz-1)`,`var(--viz-2)`,`var(--viz-3)`,`var(--viz-4)`,`var(--viz-5)`,`var(--viz-6)`,`var(--viz-7)`,`var(--viz-8)`];function k(e){return{"--curve-color":e}}var A=!1;function j(){if(A)return;A=!0,u();let t=`EmakiLevel`,n=e.localeText;(0,e.registerModuleLocale)(t,`zh-CN`,{"emakilevel.module.name":`Level`,"emakilevel.module.summary":`多等级类型、经验来源与成长配置`,"emakilevel.file.config.title":`主配置`,"emakilevel.file.config.comment":`等级系统主配置，包含语言、PDC、属性桥接、防刷和 MythicMobs 接入。`,"emakilevel.file.requirements.title":`升级需求`,"emakilevel.file.requirements.comment":`全局、分组与类型级经验需求公式。`,"emakilevel.file.types.title":`等级类型`,"emakilevel.file.types.comment":`主等级、战斗、挖掘、烹饪、锻造等等级类型。`,"emakilevel.file.sources.title":`经验来源`,"emakilevel.file.sources.comment":`Bukkit 与 MythicMobs 事件来源的经验规则。`,"emakilevel.file.gui.title":`等级 GUI`,"emakilevel.file.gui.comment":`等级 GUI 模板配置。`,"emakilevel.field.id":`ID`,"emakilevel.field.enabled":`启用`,"emakilevel.field.display_name":`显示名称`,"emakilevel.field.description":`描述`,"emakilevel.field.primary":`主等级`,"emakilevel.field.start_level":`起始等级`,"emakilevel.field.max_level":`最高等级`,"emakilevel.field.requirement":`升级需求`,"emakilevel.field.upgrade":`升级配置`,"emakilevel.field.actions":`动作`,"emakilevel.field.attributes":`属性贡献`,"emakilevel.field.sources":`来源规则`,"emakilevel.field.rules":`匹配规则`,"emakilevel.field.exp_formula":`经验公式`,"emakilevel.field.type":`等级类型`,"emakilevel.option.trigger.entity_kill":`实体击杀`,"emakilevel.option.trigger.mythic_mob_kill":`Mythic 击杀`,"emakilevel.option.trigger.block_break":`方块破坏`,"emakilevel.option.trigger.crop_harvest":`作物收获`,"emakilevel.option.trigger.player_fish":`钓鱼`,"emakilevel.option.trigger.craft_item":`合成`,"emakilevel.option.trigger.brew_complete":`炼药完成`,"emakilevel.option.trigger.furnace_extract":`冶炼提取`,"emakilevel.option.trigger.entity_tame":`驯养`}),(0,e.registerModuleLocale)(t,`en-US`,{"emakilevel.module.name":`Level`,"emakilevel.module.summary":`Level types, experience sources, and progression configuration`,"emakilevel.file.config.title":`Main config`,"emakilevel.file.config.comment":`Main level system config: language, PDC, attribute bridge, anti-abuse, and MythicMobs integration.`,"emakilevel.file.requirements.title":`Requirements`,"emakilevel.file.requirements.comment":`Global, group, and type-specific experience requirements.`,"emakilevel.file.types.title":`Level types`,"emakilevel.file.types.comment":`Main, combat, mining, cooking, forging, and other level types.`,"emakilevel.file.sources.title":`Experience sources`,"emakilevel.file.sources.comment":`Experience rules for Bukkit and MythicMobs events.`,"emakilevel.file.gui.title":`Level GUI`,"emakilevel.file.gui.comment":`Level GUI template configuration.`,"emakilevel.field.id":`ID`,"emakilevel.field.enabled":`Enabled`,"emakilevel.field.display_name":`Display name`,"emakilevel.field.description":`Description`,"emakilevel.field.primary":`Primary`,"emakilevel.field.start_level":`Start level`,"emakilevel.field.max_level":`Max level`,"emakilevel.field.requirement":`Requirement`,"emakilevel.field.upgrade":`Upgrade`,"emakilevel.field.actions":`Actions`,"emakilevel.field.attributes":`Attribute contributions`,"emakilevel.field.sources":`Source rules`,"emakilevel.field.rules":`Match rules`,"emakilevel.field.exp_formula":`Exp formula`,"emakilevel.field.type":`Level type`,"emakilevel.option.trigger.entity_kill":`Entity kill`,"emakilevel.option.trigger.mythic_mob_kill":`Mythic kill`,"emakilevel.option.trigger.block_break":`Block break`,"emakilevel.option.trigger.crop_harvest":`Crop harvest`,"emakilevel.option.trigger.player_fish":`Fishing`,"emakilevel.option.trigger.craft_item":`Craft item`,"emakilevel.option.trigger.brew_complete":`Brew complete`,"emakilevel.option.trigger.furnace_extract":`Furnace extract`,"emakilevel.option.trigger.entity_tame":`Taming`}),(0,e.registerPluginGuiEditor)({moduleId:t,editorId:`emakilevel:gui`,label:n(`等级 GUI`,`Level GUI`),fields:[[`slots`,n(`槽位`,`Slots`),n(`GUI 中所有可渲染槽位配置。`,`Renderable slots in this GUI.`),`object`],[`type`,n(`槽位类型`,`Slot type`),n(`等级 GUI 槽位语义，可选预设或自定义。`,`Level GUI slot type; preset or custom values are allowed.`),`enum`,{options:[`filler`,`level_type`,`type_info`,`progress`,`levelup`,`next_page`,`previous_page`,`close`],optionLabelPrefix:`slotType`}],[`item`,n(`物品来源`,`Item source`),n(`CoreLib ItemSource 字符串。`,`CoreLib ItemSource string.`),`text`],[`display_name`,n(`显示名称`,`Display name`),n(`槽位显示名称。`,`Slot display name.`),`text`],[`lore`,n(`Lore`,`Lore`),n(`槽位 Lore。`,`Slot lore.`),`stringList`]]}),(0,e.registerConfigMetaFields)(t,f),(0,e.registerPluginConfig)({moduleId:t,fileSchemas:[{pathPrefix:`types/`,fields:p},{pathPrefix:`sources/`,fields:m}]}),(0,e.registerConfigRuleFields)(t,h),(0,e.registerConfigPreview)({moduleId:t,kind:`CONFIG`,pathPattern:`requirements.yml`,component:g,label:n(`等级曲线`,`Level curve`),priority:20}),(0,e.registerConfigPreview)({moduleId:t,kind:`CONFIG`,pathPattern:`types/**/*.yml`,component:g,label:n(`等级曲线`,`Level curve`),priority:20}),(0,e.registerConfigCreateTemplate)(t,`sources`,{id:`source-rule`,label:n(`经验来源`,`Experience source`),fields:[{path:`enabled`,label:n(`启用`,`Enabled`),comment:n(`是否启用该来源。`,`Whether this source is enabled.`),type:`boolean`,defaultValue:!0},{path:`type`,label:n(`等级类型`,`Level type`),comment:n(`经验写入的等级类型 ID。`,`Target level type id.`),type:`text`,defaultValue:`main`},{path:`trigger`,label:n(`触发器`,`Trigger`),comment:n(`经验来源触发器。`,`Experience source trigger.`),type:`text`,defaultValue:`entity_kill`},{path:`rules`,label:n(`规则`,`Rules`),comment:n(`匹配规则列表。`,`Match rule list.`),type:`objectList`,defaultValue:[]}]})}j()})(EmakiWebConsole,React);