import { useState } from 'react';
import { ApiClient, reportFrontendLoginEvent } from '../api';
import { t } from '../i18n';
import { Button, InlineError } from '../components';

export type LoginNotice = 'expired' | 'signedOut' | null;

export function Login({ onLogin, notice }: { onLogin: (token: string) => void; notice?: LoginNotice }) {
  const [username, setUsername] = useState('EmakiAdmin');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const api = new ApiClient(null, () => { });

  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true); setError('');
    reportLoginDebug('login_submit', `username=${username.trim() || '<empty>'}; password=<masked>`);
    try {
      const result = await api.login(username, password);
      reportLoginDebug('login_success', `username=${username.trim() || '<empty>'}`);
      onLogin(result.token);
    }
    catch (err) {
      const message = err instanceof Error ? err.message : t('core.login.failed');
      reportLoginDebug('login_failed', message);
      setError(message);
    }
    finally { setBusy(false); }
  }

  function reportLoginDebug(type: string, detail: string) {
    reportFrontendLoginEvent({
      type,
      target: 'login.form',
      label: t('core.login.submit'),
      detail
    });
  }

  return (
    <main className="login-scene">
      <section className="login-panel">
        <div className="login-kicker">{t('core.login.kicker')}</div>
        <h1>{t('core.login.title')}</h1>
        <p>{t('core.login.description')}</p>
        <form onSubmit={submit}>
          <label>{t('core.login.username')}<input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" /></label>
          <label>{t('core.login.password')}<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" /></label>
          {error && <InlineError>{error}</InlineError>}
          {!error && notice === 'expired' && <InlineError>{t('core.login.sessionExpired')}</InlineError>}
          {!error && notice === 'signedOut' && <div className="login-notice" role="status">{t('core.login.signedOut')}</div>}
          <Button type="submit" variant="primary" disabled={busy || !username.trim() || !password}>{busy ? t('core.login.busy') : t('core.login.submit')}</Button>
        </form>
      </section>
    </main>
  );
}
