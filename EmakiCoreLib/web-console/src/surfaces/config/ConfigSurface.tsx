import { type ApiClient } from '../../api';
import { isGlobPath } from '../../documentPaths';
import { t } from '../../i18n';
import { fileDisplayComment, fileDisplayTitle } from '../../lib';
import { isKind, type SurfaceOutlineState, type SurfaceToolbarState } from '../../registry';
import { fileKindLabel } from '../../shell';
import type { WebRegistry, WebRegistryFile, WebRegistryModule } from '../../types';
import type { DraftHistoryMap, DraftMap, DraftPathsAction, DraftScopeAction, DraftValueSetter } from './ConfigDraftRuntime';
import { ConfigChildSurface, ConfigStructuredSurface, type ConfigSaveSafety } from './ConfigStructuredSurface';
import { type ConfigToast } from './ConfigSourceRuntime';

export function ConfigSurface({ module, file, drafts, draftHistory, setDraftValue, clearDraftScope, clearDraftValues, clearDraftPaths, reconcileScopeDrafts, setSaveConflict, undoDraftScope, redoDraftScope, api, scriptPath, refreshKey, onRefreshRegistry, setSurfaceToolbar, setSurfaceOutline, setToast }: { module: WebRegistryModule | null; file: WebRegistryFile | null; drafts: DraftMap; draftHistory: DraftHistoryMap; setDraftValue: DraftValueSetter; clearDraftScope: DraftScopeAction; clearDraftValues: DraftScopeAction; clearDraftPaths: DraftPathsAction; reconcileScopeDrafts: ConfigSaveSafety['reconcileScopeDrafts']; setSaveConflict: ConfigSaveSafety['setSaveConflict']; undoDraftScope: DraftScopeAction; redoDraftScope: DraftScopeAction; api: ApiClient; scriptPath?: string; refreshKey: number; onRefreshRegistry: () => Promise<WebRegistry | null>; setSurfaceToolbar: (state: SurfaceToolbarState | null) => void; setSurfaceOutline: (state: SurfaceOutlineState) => void; setToast: (toast: ConfigToast) => void }) {
  if (!module || !file) return <section className="config-surface empty" role="status">{t('core.empty.selectConfig')}</section>;
  if (!isKind(file.kind, 'CONFIG')) return null;
  if (scriptPath) return <ConfigChildSurface module={module} file={file} childPath={scriptPath} drafts={drafts} draftHistory={draftHistory} setDraftValue={setDraftValue} clearDraftScope={clearDraftScope} clearDraftValues={clearDraftValues} clearDraftPaths={clearDraftPaths} reconcileScopeDrafts={reconcileScopeDrafts} setSaveConflict={setSaveConflict} undoDraftScope={undoDraftScope} redoDraftScope={redoDraftScope} api={api} refreshKey={refreshKey} setSurfaceToolbar={setSurfaceToolbar} setSurfaceOutline={setSurfaceOutline} setToast={setToast} />;
  if (isGlobPath(file.path) || (file.children && file.children.length > 0 && file.nodes.length === 0)) return <section className="config-surface"><div className="surface-head"><div><h2>{fileDisplayTitle(file)}</h2><p>{fileDisplayComment(file)}</p></div><span className={`file-kind ${String(file.kind).toLowerCase()}`}>{fileKindLabel(file.kind)}</span></div><div className="script-placeholder" role="status">{t('core.empty.selectFile')}</div></section>;
  return <ConfigStructuredSurface module={module} file={file} drafts={drafts} draftHistory={draftHistory} setDraftValue={setDraftValue} clearDraftScope={clearDraftScope} clearDraftValues={clearDraftValues} clearDraftPaths={clearDraftPaths} reconcileScopeDrafts={reconcileScopeDrafts} setSaveConflict={setSaveConflict} undoDraftScope={undoDraftScope} redoDraftScope={redoDraftScope} api={api} refreshKey={refreshKey} onRefreshRegistry={onRefreshRegistry} setSurfaceToolbar={setSurfaceToolbar} setSurfaceOutline={setSurfaceOutline} setToast={setToast} />;
}
