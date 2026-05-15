import { useState } from 'react';
import { ApiClient } from '../api';

export function Login({ onLogin }: { onLogin: (token: string) => void }) {
  const [username, setUsername] = useState('EmakiAdmin');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const api = new ApiClient(null, () => {});

  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true); setError('');
    try { onLogin((await api.login(username, password)).token); }
    catch (err) { setError(err instanceof Error ? err.message : '登录失败'); }
    finally { setBusy(false); }
  }

  return (
    <main className="login-scene">
      <section className="login-panel">
        <div className="login-kicker">绘卷核心库</div>
        <h1>配置控制台</h1>
        <p>面向管理员团队的深度配置编辑工具。保存后执行 reload 使运行时生效。</p>
        <form onSubmit={submit}>
          <label>账号<input value={username} onChange={(e) => setUsername(e.target.value)} /></label>
          <label>密码<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>
          {error && <div className="inline-error" role="alert">{error}</div>}
          <button type="submit" disabled={busy}>{busy ? '验证中' : '登录'}</button>
        </form>
      </section>
    </main>
  );
}
