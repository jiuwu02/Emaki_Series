import { useState } from 'react';
import { ApiClient } from '../api';
import { t } from '../i18n';
import { Button, InlineError } from '../components';

export function Login({ onLogin, sessionExpired }: { onLogin: (token: string) => void; sessionExpired?: boolean }) {
  const [username, setUsername] = useState('EmakiAdmin');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const api = new ApiClient(null, () => { });

  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true); setError('');
    try { onLogin((await api.login(username, password)).token); }
    catch (err) { setError(err instanceof Error ? err.message : t('core.login.failed')); }
    finally { setBusy(false); }
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
          {!error && sessionExpired && <InlineError>{t('core.login.sessionExpired')}</InlineError>}
          <Button type="submit" variant="primary" disabled={busy || !username.trim() || !password}>{busy ? t('core.login.busy') : t('core.login.submit')}</Button>
        </form>
      </section>
    </main>
  );
}
