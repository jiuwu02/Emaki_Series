(function(e,t){var n=`.advancement-preview {
  margin-top: 16px;
  display: grid;
  gap: 12px;
  padding: 14px;
  color: var(--text);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 6px;
}

.advancement-preview__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.advancement-preview__header h3 {
  margin: 0;
  font-size: 16px;
  line-height: 1.35;
  letter-spacing: -.01em;
}

.advancement-preview__header p {
  max-width: 72ch;
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.45;
}

.advancement-preview__summary {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 5px;
  max-width: 48%;
}

.advancement-preview__summary code,
.advancement-preview__summary span {
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  padding: 3px 7px;
  color: var(--muted);
  background: var(--input);
  border: 1px solid var(--line);
  border-radius: 4px;
  font-size: 11px;
  line-height: 1;
}

.advancement-preview__summary code {
  color: var(--text);
}

.advancement-preview__summary .is-dirty {
  color: var(--changed-ink);
  background: var(--changed-bg);
  border-color: var(--changed-line);
}

.advancement-preview__window {
  --advancement-canvas: #383838;
  position: relative;
  overflow: hidden;
  background: #c6c6c6;
  border: 2px solid #111;
  box-shadow:
    inset 2px 2px 0 #f2f2f2,
    inset -2px -2px 0 #565656,
    0 18px 42px var(--mc-elevation);
  image-rendering: pixelated;
}

.advancement-preview__window[data-background-theme="adventure"] { --advancement-canvas: #34433a; }
.advancement-preview__window[data-background-theme="nether"] { --advancement-canvas: #3d2427; }
.advancement-preview__window[data-background-theme="end"] { --advancement-canvas: #302d3b; }
.advancement-preview__window[data-background-theme="husbandry"] { --advancement-canvas: #3c3b2c; }

.advancement-preview__window-title {
  min-height: 38px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 10px;
  padding: 6px 14px;
  color: #3f3f3f;
  background: #c6c6c6;
  border-bottom: 2px solid #565656;
  box-shadow: inset 0 -1px 0 #f2f2f2;
  font-family: ui-monospace, "Cascadia Code", monospace;
  font-size: 12px;
  text-shadow: 1px 1px 0 #fff;
}

.advancement-preview__window-title > span {
  justify-self: start;
  color: #676767;
}

.advancement-preview__window-title > strong {
  grid-column: 2;
  max-width: min(52vw, 440px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #292929;
  font-size: 13px;
  font-weight: 700;
}

.advancement-preview__tabs {
  position: absolute;
  z-index: 3;
  top: 35px;
  left: 13px;
  display: flex;
  gap: 3px;
}

.advancement-preview__tab {
  width: 38px;
  height: 34px;
  display: grid;
  place-items: center;
  background: #8b8b8b;
  border: 2px solid #1d1d1d;
  box-shadow: inset 2px 2px 0 #d7d7d7, inset -2px -2px 0 #4d4d4d;
}

.advancement-preview__tab.is-active {
  background: #c6c6c6;
  border-bottom-color: #c6c6c6;
}

.advancement-preview__viewport {
  min-height: 312px;
  max-height: 460px;
  overflow: auto;
  margin: 12px;
  background-color: var(--advancement-canvas);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'%3E%3Cpath fill='%23ffffff' fill-opacity='.028' d='M0 0h16v16H0zm16 16h16v16H16z'/%3E%3Cpath fill='%23000000' fill-opacity='.035' d='M16 0h16v16H16zM0 16h16v16H0z'/%3E%3C/svg%3E");
  border: 2px solid #1a1a1a;
  box-shadow: inset 2px 2px 0 rgba(255,255,255,.12), inset -2px -2px 0 rgba(0,0,0,.48);
  scrollbar-color: #6f6f6f #252525;
  scrollbar-width: thin;
}

.advancement-preview__canvas {
  position: relative;
  min-width: 100%;
  min-height: 100%;
}

.advancement-preview__links {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}

.advancement-preview__links path {
  fill: none;
  stroke: #121212;
  stroke-width: 7;
  stroke-linejoin: bevel;
}

.advancement-preview__node {
  position: absolute;
  z-index: 2;
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  padding: 0;
  color: #fff;
  background: #8b8b8b;
  border: 3px solid #1b1b1b;
  border-radius: 0;
  box-shadow: inset 2px 2px 0 #d8d8d8, inset -2px -2px 0 #4a4a4a, 0 2px 0 rgba(0,0,0,.4);
  cursor: pointer;
}

.advancement-preview__node.frame-goal {
  background: #b69132;
  border-color: #382b0b;
  box-shadow: inset 2px 2px 0 #f0d267, inset -2px -2px 0 #735619, 0 2px 0 rgba(0,0,0,.45);
}

.advancement-preview__node.frame-challenge {
  background: #74648d;
  border-color: #24162f;
  box-shadow: inset 2px 2px 0 #b8a8ce, inset -2px -2px 0 #473653, 0 2px 0 rgba(0,0,0,.45);
}

.advancement-preview__node:hover,
.advancement-preview__node:focus-visible,
.advancement-preview__node.is-selected {
  z-index: 5;
  outline: 2px solid #fff;
  outline-offset: 2px;
}

.advancement-preview__node:focus-visible {
  outline-color: var(--accent-strong);
}

.advancement-preview__node.is-hidden-node {
  opacity: .62;
}

.advancement-preview__node-bevel {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  background: #242424;
  border: 2px solid #101010;
  box-shadow: inset 1px 1px 0 #555, inset -1px -1px 0 #090909;
}

.advancement-preview__item {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  overflow: hidden;
  color: #f1f1f1;
  font-family: ui-monospace, "Cascadia Code", monospace;
  font-size: 8px;
  font-weight: 800;
  line-height: 1;
  text-align: center;
  text-shadow: 1px 1px 0 #000;
}

.advancement-preview__item img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  image-rendering: pixelated;
}

.advancement-preview__tooltip {
  position: absolute;
  z-index: 8;
  top: 52px;
  left: -42px;
  width: 218px;
  display: none;
  padding: 8px 10px;
  color: #d7d7d7;
  background: rgba(16, 4, 27, .97);
  border: 2px solid #4d1c72;
  box-shadow: inset 0 0 0 1px #17071f, 4px 5px 0 rgba(0,0,0,.42);
  font-family: ui-monospace, "Cascadia Code", monospace;
  font-size: 11px;
  line-height: 1.35;
  text-align: left;
  white-space: normal;
  pointer-events: none;
}

.advancement-preview__node:hover .advancement-preview__tooltip,
.advancement-preview__node:focus-visible .advancement-preview__tooltip,
.advancement-preview__tooltip.is-pinned {
  display: grid;
  gap: 4px;
}

.advancement-preview__tooltip strong {
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.advancement-preview__tooltip-description {
  color: #aaa;
}

.advancement-preview__tooltip-flags {
  min-height: 12px;
  color: #f0c76d;
  font-size: 10px;
}

.advancement-preview__empty {
  min-height: 270px;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 6px;
  padding: 28px;
  color: #d2d2d2;
  text-align: center;
}

.advancement-preview__empty strong {
  color: #fff;
  font-family: ui-monospace, "Cascadia Code", monospace;
  font-size: 13px;
}

.advancement-preview__empty span {
  max-width: 56ch;
  color: #aaa;
  font-size: 12px;
  line-height: 1.45;
}

.advancement-preview__status {
  min-height: 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 5px 12px 7px;
  color: #555;
  background: #c6c6c6;
  border-top: 1px solid #efefef;
  font-family: ui-monospace, "Cascadia Code", monospace;
  font-size: 10px;
}

.advancement-preview__status code {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #555;
}

.advancement-preview__status span {
  flex: 0 0 auto;
  color: #333;
}

@media (max-width: 920px) {
  .advancement-preview {
    padding: 12px;
  }

  .advancement-preview__header {
    display: grid;
  }

  .advancement-preview__summary {
    justify-content: flex-start;
    max-width: none;
  }

  .advancement-preview__viewport {
    min-height: 300px;
    margin: 10px;
  }

  .advancement-preview__node {
    min-width: 46px;
    min-height: 46px;
  }

  .advancement-preview__tooltip {
    left: -28px;
    width: 188px;
  }

  .advancement-preview__status {
    align-items: flex-start;
    flex-direction: column;
    gap: 3px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .advancement-preview *,
  .advancement-preview *::before,
  .advancement-preview *::after {
    scroll-behavior: auto !important;
    transition: none !important;
  }
}
`,r=e.localeText,i=46,a=96,o=76;function s(){(0,e.injectExtensionStyles)(`emakicodex-advancement-preview`,n)}function c({data:n,sourceDirty:i}){let a=_(n.page_id,`advancement_page`),o=_(n.title,r(`未命名成就页`,`Untitled advancement page`)),s=_(n.background,`minecraft:textures/gui/advancements/backgrounds/stone.png`),c=_(n.root),p=(0,t.useMemo)(()=>d(n.advancements),[n.advancements]),m=(0,t.useMemo)(()=>f(p,c),[p,c]),v=c&&p.some(e=>e.id===c)?c:p.find(e=>!e.parent)?.id??p[0]?.id??``,y=p.map(e=>e.id).join(`\0`),[x,S]=(0,t.useState)(``);(0,t.useEffect)(()=>{x&&!p.some(e=>e.id===x)&&S(``)},[y,x]);let C=p.find(e=>e.id===x),w=h(s);return React.createElement(`section`,{className:`advancement-preview`},React.createElement(`header`,{className:`advancement-preview__header`},React.createElement(`div`,null,React.createElement(`h3`,null,r(`原版成就预览`,`Vanilla advancement preview`)),React.createElement(`p`,null,r(`使用当前草稿实时生成节点、父子连线与坐标布局；点击节点可固定物品提示。`,`Uses the current draft to render nodes, parent links, and coordinates in real time. Select a node to pin its item tooltip.`))),React.createElement(`div`,{className:`advancement-preview__summary`,"aria-label":r(`成就页摘要`,`Advancement page summary`)},React.createElement(`code`,null,a),React.createElement(`span`,null,p.length,` `,r(`节点`,`nodes`)),React.createElement(`span`,null,m.mode===`coordinates`?r(`坐标布局`,`Coordinate layout`):r(`父子树布局`,`Parent tree layout`)),i?React.createElement(`span`,{className:`is-dirty`},r(`源码草稿`,`Source draft`)):null)),React.createElement(`div`,{className:`advancement-preview__window`,"data-background-theme":w},React.createElement(`div`,{className:`advancement-preview__window-title`},React.createElement(`span`,null,r(`成就`,`Advancements`)),React.createElement(`strong`,null,React.createElement(e.MiniText,{value:o}))),React.createElement(`div`,{className:`advancement-preview__tabs`,"aria-hidden":`true`},React.createElement(`span`,{className:`advancement-preview__tab is-active`},React.createElement(u,{source:p.find(e=>e.id===v)?.icon??`minecraft-book`}))),React.createElement(`div`,{className:`advancement-preview__viewport`},p.length?React.createElement(`div`,{className:`advancement-preview__canvas`,style:{width:m.width,height:m.height}},React.createElement(`svg`,{className:`advancement-preview__links`,viewBox:`0 0 ${m.width} ${m.height}`,"aria-hidden":`true`},m.links.map(e=>React.createElement(`path`,{key:e.id,d:e.d}))),m.nodes.map(e=>React.createElement(`button`,{key:e.id,type:`button`,className:`advancement-preview__node frame-${e.frame}${e.id===x?` is-selected`:``}${e.hidden?` is-hidden-node`:``}`,style:{left:e.left,top:e.top},onClick:()=>S(t=>t===e.id?``:e.id),"aria-label":`${b(e.title)||e.id} · ${e.frame}`,"aria-pressed":e.id===x},React.createElement(`span`,{className:`advancement-preview__node-bevel`},React.createElement(u,{source:e.icon})),React.createElement(l,{node:e,pinned:e.id===x})))):React.createElement(`div`,{className:`advancement-preview__empty`},React.createElement(`strong`,null,r(`还没有成就节点`,`No advancement nodes yet`)),React.createElement(`span`,null,r(`在下方“成就节点”对象中新增节点后，这里会立即显示原版布局预览。`,`Add a child under the Advancement nodes object below to render it immediately in this vanilla-style preview.`)))),React.createElement(`footer`,{className:`advancement-preview__status`},React.createElement(`code`,null,s),C?React.createElement(`span`,null,C.id,` · `,g(C.frame)):null)))}function l({node:t,pinned:n}){let i=[t.toast?r(`Toast`,`Toast`):``,t.announce?r(`广播`,`Announce`):``,t.hidden?r(`隐藏`,`Hidden`):``].filter(Boolean);return React.createElement(`span`,{className:`advancement-preview__tooltip${n?` is-pinned`:``}`,role:`tooltip`},React.createElement(`strong`,null,React.createElement(e.MiniText,{value:t.title||t.id})),React.createElement(`span`,{className:`advancement-preview__tooltip-description`},React.createElement(e.MiniText,{value:t.description||r(`暂无描述`,`No description`)})),React.createElement(`span`,{className:`advancement-preview__tooltip-flags`},i.length?i.join(` · `):g(t.frame)))}function u({source:n}){let r=(0,e.materialFromItemSource)(n),i=(0,e.materialUrls)(r),[a,o]=(0,t.useState)(0),[,s]=(0,t.useState)(0);(0,t.useEffect)(()=>o(0),[r]),(0,t.useEffect)(()=>(0,e.subscribeTextureBases)(()=>{o(0),s(e=>e+1)}),[]);let c=i[a];return React.createElement(`span`,{className:`advancement-preview__item`,title:r},c?React.createElement(`img`,{src:c,alt:``,draggable:!1,onError:()=>o(e=>e+1)}):React.createElement(`span`,null,(0,e.materialShortName)(r)||`?`))}function d(t){return Object.entries((0,e.asRecord)(t)).map(([t,n])=>{let r=(0,e.asRecord)(n),i=_(r.frame,`task`).toLowerCase();return{id:t,icon:_(r.icon,`minecraft-book`),title:_(r.title,t),description:_(r.description),frame:i===`goal`||i===`challenge`?i:`task`,parent:_(r.parent),x:v(r.x),y:v(r.y),toast:y(r.toast,!0),announce:y(r.announce,!1),hidden:y(r.hidden,!1)}})}function f(e,t){if(!e.length)return{nodes:[],links:[],width:680,height:270,mode:`tree`};let n=e.every(e=>e.x!==0||e.y!==0),r=n?p(e):m(e,t),s=[...r.values()].map(e=>e.x),c=[...r.values()].map(e=>e.y),l=Math.min(...s),u=Math.min(...c),d=e.map(e=>{let t=r.get(e.id)??{x:0,y:0};return{...e,left:78+(t.x-l)*a,top:54+(t.y-u)*o}}),f=new Map(d.map(e=>[e.id,e])),h=Math.max(...d.map(e=>e.left)),g=Math.max(...d.map(e=>e.top)),_=Math.max(680,h+180),v=Math.max(270,g+140);return{nodes:d,links:d.flatMap(e=>{let t=f.get(e.parent);if(!t)return[];let n=t.left+i,r=t.top+i/2,a=e.left,o=e.top+i/2,s=n+(a-n)/2;return[{id:`${t.id}:${e.id}`,d:`M ${n} ${r} H ${s} V ${o} H ${a}`}]}),width:_,height:v,mode:n?`coordinates`:`tree`}}function p(e){return new Map(e.map(e=>[e.id,{x:e.x,y:e.y}]))}function m(e,t){let n=new Map(e.map(e=>[e.id,e])),r=new Map;e.forEach(e=>{!e.parent||!n.has(e.parent)||r.set(e.parent,[...r.get(e.parent)??[],e.id])});let i=new Map,a=new Set,o=0,s=(e,t)=>{let n=i.get(e);if(n)return n.y;if(a.has(e)){let n=o++;return i.set(e,{x:t,y:n}),n}a.add(e);let c=(r.get(e)??[]).map(e=>s(e,t+1)),l=c.length?c.reduce((e,t)=>e+t,0)/c.length:o++;return i.set(e,{x:t,y:l}),a.delete(e),l};return e.filter(e=>e.id===t||!e.parent||!n.has(e.parent)).forEach(e=>s(e.id,0)),e.forEach(e=>{i.has(e.id)||s(e.id,0)}),i}function h(e){let t=e.toLowerCase();return t.includes(`nether`)?`nether`:t.includes(`end`)?`end`:t.includes(`adventure`)?`adventure`:t.includes(`husbandry`)?`husbandry`:`stone`}function g(e){return e===`goal`?r(`目标`,`Goal`):e===`challenge`?r(`挑战`,`Challenge`):r(`进度`,`Task`)}function _(e,t=``){return String(e??``).trim()||t}function v(e){let t=Number(e);return Number.isFinite(t)?t:0}function y(e,t){return typeof e==`boolean`?e:t}function b(e){return e.replace(/<[^>]*>/g,``).trim()}var x=!1;function S(){if(x)return;x=!0;let t=`EmakiCodex`,n=e.localeText;s();let r=[`entity_kill`,`mythic_mob_kill`,`block_break`,`crop_harvest`,`craft_item`,`furnace_extract`,`player_fish`,`brew_complete`,`entity_tame`],i=[{path:`icon`,label:n(`图标`,`Icon`),comment:n(`CoreLib ItemSource 字符串。`,`CoreLib ItemSource string.`),type:`text`,defaultValue:`minecraft-book`},{path:`title`,label:n(`标题`,`Title`),comment:n(`成就标题，支持 MiniMessage。`,`Advancement title with MiniMessage support.`),type:`text`,defaultValue:`<gold>新成就</gold>`},{path:`description`,label:n(`描述`,`Description`),comment:n(`成就描述，支持 MiniMessage。`,`Advancement description with MiniMessage support.`),type:`textarea`,defaultValue:`<gray>描述</gray>`},{path:`frame`,label:n(`边框`,`Frame`),comment:n(`原版成就边框类型。`,`Vanilla advancement frame type.`),type:`enum`,options:[`task`,`goal`,`challenge`],defaultValue:`task`},{path:`parent`,label:n(`父节点`,`Parent`),comment:n(`父成就的本地 ID；根节点留空。`,`Local parent advancement id; leave empty for the root.`),type:`text`,defaultValue:``},{path:`x`,label:`X`,comment:n(`PacketEvents 坐标横轴。`,`PacketEvents horizontal coordinate.`),type:`number`,defaultValue:0},{path:`y`,label:`Y`,comment:n(`PacketEvents 坐标纵轴。`,`PacketEvents vertical coordinate.`),type:`number`,defaultValue:0},{path:`toast`,label:n(`显示 Toast`,`Show toast`),comment:n(`完成时是否显示客户端 Toast。`,`Whether to show a client toast on completion.`),type:`boolean`,defaultValue:!0},{path:`announce`,label:n(`全服广播`,`Announce`),comment:n(`完成时是否全服广播。`,`Whether completion is announced globally.`),type:`boolean`,defaultValue:!1},{path:`hidden`,label:n(`隐藏`,`Hidden`),comment:n(`未完成前是否隐藏节点。`,`Whether to hide the node until completed.`),type:`boolean`,defaultValue:!1},{path:`actions`,label:n(`完成动作`,`Completion actions`),comment:n(`节点完成时执行的 CoreLib Actions。`,`CoreLib Actions executed when the node completes.`),type:`object`,defaultValue:{complete:[]}},{path:`triggers`,label:n(`事件触发器`,`Event triggers`),comment:n(`满足任一触发器时自动授予。`,`Automatically grant when any trigger matches.`),type:`object`,defaultValue:{entries:[]}}];(0,e.registerModuleLocale)(t,`zh-CN`,{"emakicodex.module.name":`Codex`,"emakicodex.module.summary":`原版成就页、节点、触发器与完成动作配置`,"emakicodex.file.config.title":`主配置`,"emakicodex.file.config.comment":`语言、默认数据、权限绕过与原版成就运行设置。`,"emakicodex.file.advancements.title":`成就页`,"emakicodex.file.advancements.comment":`每个 YAML 文件对应一个原版成就标签页。`,"emakicodex.file.lang.title":`语言文件`,"emakicodex.file.lang.comment":`Codex 控制台、命令与运行时消息。`}),(0,e.registerModuleLocale)(t,`en-US`,{"emakicodex.module.name":`Codex`,"emakicodex.module.summary":`Vanilla advancement pages, nodes, triggers, and completion actions`,"emakicodex.file.config.title":`Main config`,"emakicodex.file.config.comment":`Language, default data, permission bypass, and advancement runtime settings.`,"emakicodex.file.advancements.title":`Advancement pages`,"emakicodex.file.advancements.comment":`Each YAML file defines one vanilla advancement tab.`,"emakicodex.file.lang.title":`Language files`,"emakicodex.file.lang.comment":`Codex console, command, and runtime messages.`}),(0,e.registerInsightDefinition)({moduleId:t,pathPrefix:`advancements/`,idType:`advancement_page`,idPath:`page_id`}),(0,e.registerConfigPreview)({moduleId:t,kind:`CONFIG`,pathPrefix:`advancements/`,component:c,label:n(`原版成就预览`,`Vanilla advancement preview`),priority:20}),(0,e.registerPluginConfig)({moduleId:t,metaFields:[[`version`,n(`版本`,`Version`),n(`由资源同步维护的配置版本。`,`Configuration version maintained by resource sync.`),`text`],[`language`,n(`语言`,`Language`),n(`使用的语言文件 ID。`,`Language bundle id.`),`text`],[`release_default_data`,n(`释放默认数据`,`Release default data`),n(`是否生成示例成就页。`,`Whether to generate the example advancement page.`),`boolean`],[`op_bypass`,n(`OP 绕过`,`OP bypass`),n(`OP 是否绕过命令权限。`,`Whether operators bypass command permissions.`),`boolean`],[`advancement`,n(`原版成就`,`Vanilla advancements`),n(`动态成就注册、发包坐标与事件触发设置。`,`Dynamic registration, packet coordinates, and event trigger settings.`),`object`],[`advancement.enabled`,n(`启用成就`,`Enable advancements`),n(`是否启用动态原版成就。`,`Whether dynamic vanilla advancements are enabled.`),`boolean`],[`advancement.platform`,n(`注册平台`,`Registration platform`),n(`当前使用 unsafe 动态注册平台。`,`Currently uses the unsafe dynamic registration platform.`),`enum`,{options:[`unsafe`]}],[`advancement.announce-default`,n(`默认广播`,`Default announce`),n(`节点未单独配置时是否全服广播。`,`Default global announcement when a node does not override it.`),`boolean`],[`advancement.remove-on-disable`,n(`禁用时移除`,`Remove on disable`),n(`禁用或重载时移除动态成就。`,`Remove dynamic advancements on disable or reload.`),`boolean`],[`advancement.packet-coordinates`,n(`发包坐标`,`Packet coordinates`),n(`安装 PacketEvents 时使用节点 x/y 坐标。`,`Use node x/y coordinates when PacketEvents is installed.`),`boolean`],[`advancement.triggers-enabled`,n(`事件触发器`,`Event triggers`),n(`是否启用 triggers.entries 自动授予。`,`Whether triggers.entries can automatically grant advancements.`),`boolean`],[`debug`,`Debug`,n(`是否启用 Codex 调试输出。`,`Whether Codex debug output is enabled.`),`boolean`]],fileSchemas:[{pathPrefix:`advancements/`,fields:[[`page_id`,n(`页面 ID`,`Page id`),n(`成就 key 的路径前缀。`,`Path prefix used by advancement keys.`),`text`],[`title`,n(`标签页标题`,`Tab title`),n(`原版成就标签页标题，支持 MiniMessage。`,`Vanilla advancement tab title with MiniMessage support.`),`text`],[`background`,n(`背景`,`Background`),n(`资源包中的成就背景纹理路径。`,`Advancement background texture path.`),`text`],[`root`,n(`根节点`,`Root node`),n(`根成就的本地 ID。`,`Local id of the root advancement.`),`text`],[`advancements`,n(`成就节点`,`Advancement nodes`),n(`以本地 ID 为键的成就节点。`,`Advancement nodes keyed by local id.`),`object`,{creatableChildren:!0}]]}],rules:[[{suffix:`.actions.complete`},{label:n(`完成动作`,`Completion actions`),comment:n(`节点完成时执行的 CoreLib Actions。`,`CoreLib Actions executed on completion.`),type:`actions`}],[{suffix:`.triggers.entries`},{label:n(`触发器列表`,`Trigger list`),comment:n(`任一触发器匹配即授予该节点。`,`Grant the node when any trigger matches.`),type:`objectList`}],[{suffix:`.condition.entries`},{label:n(`条件表达式`,`Condition expressions`),comment:n(`CoreLib 条件组表达式列表。`,`CoreLib condition group expression list.`),type:`stringList`}],[{key:`frame`},{label:n(`边框`,`Frame`),type:`enum`,options:[`task`,`goal`,`challenge`]}],[{key:`icon`},{label:n(`图标`,`Icon`),type:`text`}],[{key:`description`},{label:n(`描述`,`Description`),type:`textarea`}],[{key:`parent`},{label:n(`父节点`,`Parent`),type:`text`}],[{key:`toast`},{label:n(`显示 Toast`,`Show toast`),type:`boolean`}],[{key:`announce`},{label:n(`全服广播`,`Announce`),type:`boolean`}],[{key:`hidden`},{label:n(`隐藏`,`Hidden`),type:`boolean`}]],createTemplates:[[`advancements`,{id:`advancement-node`,label:n(`成就节点`,`Advancement node`),fields:i}]],listItemSchemaRules:[[{suffix:`.triggers.entries`},[{path:`event`,label:n(`事件`,`Event`),comment:n(`触发该节点的游戏事件。`,`Game event that triggers this node.`),type:`enum`,options:r,defaultValue:`entity_kill`},{path:`condition`,label:n(`条件`,`Condition`),comment:n(`可选 CoreLib 条件组。`,`Optional CoreLib condition group.`),type:`object`,defaultValue:{type:`all_of`,entries:[]}}]]]})}S()})(EmakiWebConsole,React);