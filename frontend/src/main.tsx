import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

type Csrf = { headerName: string; token: string };
type Account = { username: string; organizationId: string; role: 'ADMIN' | 'MEMBER' };
type SecurityEvent = { id: number; actorUsername: string; eventType: string; targetUsername: string; createdAt: string };
type TeamMember = { id: number; username: string; role: string; enabled: boolean; createdAt: string };
type Status = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'WON' | 'LOST';
type Lead = { id: number; name: string; email: string; note: string | null; status: Status };
type StatusFilter = Status | 'ALL';
type LeadTask = { id: number; leadId: number; title: string; dueDate: string; status: 'OPEN' | 'DONE' };
type LeadActivity = { id: number; kind: 'CREATED' | 'STATUS_CHANGED' | 'NOTE_UPDATED'; summary: string; createdAt: string };
const API = '/api/leads';
const TASK_API = '/api/tasks';
function localToday() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; }
const statuses: Status[] = ['NEW', 'CONTACTED', 'QUALIFIED', 'WON', 'LOST'];
const labels: Record<Status, string> = {
  NEW: 'New', CONTACTED: 'Contacted', QUALIFIED: 'Qualified', WON: 'Won', LOST: 'Lost'
};

function App() {
  const [auth, setAuth] = useState(false);
  const [csrf, setCsrf] = useState<Csrf | null>(null);
  const [restoringSession, setRestoringSession] = useState(true);
  const [account, setAccount] = useState<Account | null>(null);
  const [team, setTeam] = useState<TeamMember[]>([]);
  const [securityEvents, setSecurityEvents] = useState<SecurityEvent[]>([]);
  const [memberUsername, setMemberUsername] = useState('');
  const [memberPassword, setMemberPassword] = useState('');
  const [teamSaving, setTeamSaving] = useState(false);
  const [teamMessage, setTeamMessage] = useState('');
  const [resetMemberId, setResetMemberId] = useState<number | null>(null);
  const [resetAdminPassword, setResetAdminPassword] = useState('');
  const [resetNewPassword, setResetNewPassword] = useState('');
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [authError, setAuthError] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordMessage, setPasswordMessage] = useState('');
  const [leads, setLeads] = useState<Lead[]>([]);
  const [tasks, setTasks] = useState<LeadTask[]>([]);
  const [taskTitle, setTaskTitle] = useState('');
  const [taskDue, setTaskDue] = useState(localToday());
  const [taskFilter, setTaskFilter] = useState<'OPEN' | 'ALL' | 'DONE' | 'OVERDUE' | 'TODAY' | 'UPCOMING'>('OPEN');
  const [taskSearch, setTaskSearch] = useState('');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<StatusFilter>('ALL');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [note, setNote] = useState('');
  const [activity, setActivity] = useState<LeadActivity[]>([]);
  const [activityLoading, setActivityLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const canEdit = account?.role === 'ADMIN';
  const selected = leads.find(lead => lead.id === selectedId) ?? null;
  const shownTasks = useMemo(() => {
    const today = localToday();
    const query = taskSearch.trim().toLocaleLowerCase();
    return tasks.filter(task => {
      const matchesStatus = taskFilter === 'ALL'
        || (taskFilter === 'OPEN' && task.status === 'OPEN')
        || (taskFilter === 'DONE' && task.status === 'DONE')
        || (task.status === 'OPEN' && (
          (taskFilter === 'OVERDUE' && task.dueDate < today)
          || (taskFilter === 'TODAY' && task.dueDate === today)
          || (taskFilter === 'UPCOMING' && task.dueDate > today)));
      const customer = leads.find(lead => lead.id === task.leadId);
      const matchesSearch = !query || [task.title, customer?.name ?? '', customer?.email ?? '']
        .some(value => value.toLocaleLowerCase().includes(query));
      return matchesStatus && matchesSearch;
    }).sort((a, b) => a.dueDate.localeCompare(b.dueDate) || a.id - b.id);
  }, [tasks, taskFilter, taskSearch, leads]);
  const overdue = tasks.filter(task => task.status === 'OPEN' && task.dueDate < localToday()).length;
  const upcoming = tasks.filter(task => task.status === 'OPEN' && task.dueDate >= localToday()).length;
  const wonCount = leads.filter(lead => lead.status === 'WON').length;
  const lostCount = leads.filter(lead => lead.status === 'LOST').length;
  const closedCount = wonCount + lostCount;
  const conversionRate = leads.length ? Math.round(wonCount / leads.length * 100) : 0;
  const winRate = closedCount ? Math.round(wonCount / closedCount * 100) : 0;
  const doneCount = tasks.filter(task => task.status === 'DONE').length;
  const taskCompletionRate = tasks.length ? Math.round(doneCount / tasks.length * 100) : 0;
  const pipelineCounts = statuses.map(status => ({
    status, count: leads.filter(lead => lead.status === status).length
  }));
  const leadName = (id: number) => leads.find(lead => lead.id === id)?.name ?? `Lead #${id}`;
  const filtered = useMemo(() => leads.filter(lead => {
    const q = search.trim().toLocaleLowerCase();
    const matchesSearch = !q || [lead.name, lead.email, lead.note ?? '']
      .some(value => value.toLocaleLowerCase().includes(q));
    return matchesSearch && (filter === 'ALL' || lead.status === filter);
  }), [leads, search, filter]);

  function clearClientSession() {
    setAuth(false);
    setAccount(null);
    setTeam([]);
    setSecurityEvents([]);
    setMemberUsername('');
    setMemberPassword('');
    setTeamMessage('');
    setResetMemberId(null);
    setResetAdminPassword('');
    setResetNewPassword('');
    setPassword('');
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setPasswordMessage('');
    setLeads([]);
    setTasks([]);
    setTaskFilter('OPEN');
    setTaskSearch('');
    setSelectedId(null);
    setNote('');
    setActivity([]);
    setError('');
    setSuccess('');
  }

  async function refreshCsrf(): Promise<Csrf> {
    const response = await fetch('/api/session/csrf', { credentials: 'same-origin' });
    if (!response.ok) throw new Error('CSRF unavailable');
    const value = await response.json() as Csrf;
    setCsrf(value);
    return value;
  }

  async function api(url: string, init: RequestInit = {}) {
    const method = (init.method ?? 'GET').toUpperCase();
    const unsafe = !['GET', 'HEAD', 'OPTIONS'].includes(method);
    const token = unsafe ? (csrf ?? await refreshCsrf()) : null;
    const response = await fetch(url, {
      ...init,
      credentials: 'same-origin',
      headers: { ...init.headers, ...(token ? { [token.headerName]: token.token } : {}) }
    });
    if (response.status === 401) {
      clearClientSession();
      setAuthError('Your session expired. Please sign in again.');
      throw new Error('Unauthorized');
    }
    if (!response.ok) throw new Error(`Request failed (${response.status})`);
    return response;
  }

  async function signOut(forceLocal = false) {
    try {
      const token = csrf ?? await refreshCsrf();
      const response = await fetch('/api/session/logout', {
        method: 'POST', credentials: 'same-origin',
        headers: { [token.headerName]: token.token }
      });
      if (!response.ok && response.status !== 401) {
        throw new Error('Logout rejected');
      }
      clearClientSession();
      setCsrf(null);
    } catch {
      if (forceLocal) {
        // Password rotation already invalidated the server session.
        clearClientSession();
        setCsrf(null);
      } else {
        setError('Could not confirm logout with the local server. Try again.');
      }
    }
  }

  useEffect(() => {
    let cancelled = false;
    async function restore() {
      try {
        const token = await refreshCsrf();
        const response = await fetch('/api/me', { credentials: 'same-origin' });
        if (!cancelled && response.ok) {
          setCsrf(token);
          setAccount(await response.json() as Account);
          setAuth(true);
        }
      } catch {
        // No session or backend unavailable.
      } finally {
        if (!cancelled) setRestoringSession(false);
      }
    }
    void restore();
    return () => { cancelled = true; };
  }, []);

  async function signIn(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAuthError('');
    setLoading(true);
    try {
      const token = csrf ?? await refreshCsrf();
      const response = await fetch('/api/session/login', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', [token.headerName]: token.token },
        body: JSON.stringify({ username, password })
      });
      if (response.status === 429) {
        setAuthError('Too many failed sign-in attempts. Try again later.');
        return;
      }
      if (!response.ok) throw new Error('Invalid credentials');
      const currentAccount = await response.json() as Account;
      setAccount(currentAccount);
      setAuth(true);
      setPassword('');
      setError('');
      await refreshCsrf();
    } catch {
      setAuthError('Sign in failed. Check your credentials and backend connection.');
    } finally {
      setLoading(false);
    }
  }

  async function load() {
    try {
      const response = await api(API);
      setLeads(await response.json() as Lead[]);
      setError('');
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unauthorized') {
        setError('Cannot load leads. Check the backend connection.');
      }
    }
  }

  async function downloadCsv(kind: 'leads' | 'tasks') {
    setError('');
    try {
      const response = await api(`/api/exports/${kind}.csv`);
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `nexora-${kind}.csv`;
      document.body.appendChild(link);
      try {
        link.click();
      } finally {
        link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      }
    } catch (error) {
      if (error instanceof Error && error.message !== 'Unauthorized') {
        setError('Could not export data. Check your connection and try again.');
      }
    }
  }

  async function loadTasks() {
    try {
      const response = await api(TASK_API);
      setTasks(await response.json() as LeadTask[]);
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unauthorized') setError('Could not load follow-up tasks.');
    }
  }

  async function loadTeam() {
    if (!canEdit) return;
    try {
      const response = await api('/api/team');
      setTeam(await response.json() as TeamMember[]);
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unauthorized') setError('Could not load team members.');
    }
  }

  async function loadSecurityEvents() {
    if (!canEdit) return;
    try {
      const response = await api('/api/security/events');
      setSecurityEvents(await response.json() as SecurityEvent[]);
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unauthorized') setError('Could not load security history.');
    }
  }

  async function changeMyPassword(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPasswordMessage('');
    if (newPassword !== confirmPassword) {
      setPasswordMessage('New passwords do not match.');
      return;
    }
    if (newPassword.length < 12 || newPassword.length > 128) {
      setPasswordMessage('New password must be 12–128 characters.');
      return;
    }
    setPasswordSaving(true);
    try {
      const response = await api('/api/me/password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ currentPassword, newPassword })
      });
      if (!response.ok) throw new Error('Password change failed');
      await signOut(true);
      setAuthError('Password changed. Please sign in with your new password.');
    } catch (error) {
      if (error instanceof Error && error.message !== 'Unauthorized') {
        setPasswordMessage('Could not update password. Check your current password and choose a different password.');
      }
    } finally {
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setPasswordSaving(false);
    }
  }

  async function addMember(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canEdit) return;
    setTeamSaving(true);
    setTeamMessage('');
    try {
      const response = await api('/api/team', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: memberUsername.trim(), password: memberPassword })
      });
      if (response.status !== 201) throw new Error('Failed to create account');
      setMemberUsername('');
      setMemberPassword('');
      await loadTeam();
      setTeamMessage('Member created. Share the password securely outside Nexora.');
    } catch (error) {
      if (error instanceof Error && error.message !== 'Unauthorized') {
        setTeamMessage('Could not add member. Check username uniqueness and password length.');
      }
    } finally {
      setTeamSaving(false);
    }
  }

  async function toggleMember(member: TeamMember) {
    if (!canEdit || member.username === account?.username || member.role !== 'MEMBER') return;
    setTeamSaving(true);
    setTeamMessage('');
    try {
      await api(`/api/team/${member.id}/enabled`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled: !member.enabled })
      });
      await loadTeam();
      setTeamMessage(member.enabled ? 'Member disabled.' : 'Member enabled.');
    } catch (error) {
      if (error instanceof Error && error.message !== 'Unauthorized') {
        setTeamMessage('Could not update member access.');
      }
    } finally {
      setTeamSaving(false);
    }
  }

  async function resetMemberPassword(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canEdit || resetMemberId === null) return;
    setTeamSaving(true);
    setTeamMessage('');
    try {
      await api(`/api/team/${resetMemberId}/reset-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          adminPassword: resetAdminPassword,
          newPassword: resetNewPassword
        })
      });
      setTeamMessage('Password reset. Share the new password with the member securely.');
      setResetMemberId(null);
    } catch (error) {
      if (error instanceof Error && error.message !== 'Unauthorized') {
        setTeamMessage('Reset failed. Verify your admin password, member status and new password.');
      }
    } finally {
      setResetAdminPassword('');
      setResetNewPassword('');
      setTeamSaving(false);
    }
  }

  useEffect(() => { if (auth) { void load(); void loadTasks(); } }, [auth]);
  useEffect(() => { if (auth && canEdit) { void loadTeam(); void loadSecurityEvents(); } }, [auth, canEdit]);

  async function addTask(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected || !canEdit) return;
    setSaving(true); setError(''); setSuccess('');
    try {
      await api(TASK_API, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ leadId: selected.id, title: taskTitle.trim(), dueDate: taskDue })
      });
      setTaskTitle('');
      await loadTasks();
      setSuccess('Follow-up task created.');
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unauthorized') setError('Could not create task.');
    } finally { setSaving(false); }
  }

  async function updateTask(id: number, status: 'OPEN' | 'DONE') {
    setSaving(true); setError(''); setSuccess('');
    try {
      await api(`${TASK_API}/${id}/status`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status })
      });
      await loadTasks();
      setSuccess(status === 'DONE' ? 'Task completed.' : 'Task reopened.');
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unauthorized') setError('Could not update task.');
    } finally { setSaving(false); }
  }

  async function add(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError('');
    setSuccess('');
    try {
      const response = await api(API, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: name.trim(), email: email.trim() })
      });
      const created = await response.json() as Lead;
      setName('');
      setEmail('');
      await load();
      await loadActivity(created.id);
      setSelectedId(created.id);
      setNote(created.note ?? '');
      setSuccess('Lead added successfully.');
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unauthorized') {
        setError('Could not create lead. Check the supplied details.');
      }
    } finally {
      setSaving(false);
    }
  }

  async function changeStatus(id: number, status: Status) {
    setError('');
    setSuccess('');
    setSaving(true);
    try {
      await api(`${API}/${id}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status })
      });
      await load();
      if (selectedId === id) await loadActivity(id);
      setSuccess('Status updated.');
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unauthorized') setError('Could not update status.');
    } finally {
      setSaving(false);
    }
  }

  async function saveNote(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    setError('');
    setSuccess('');
    setSaving(true);
    try {
      await api(`${API}/${selected.id}/note`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ note })
      });
      await load();
      await loadActivity(selected.id);
      setSuccess('Note saved.');
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unauthorized') setError('Could not save note.');
    } finally {
      setSaving(false);
    }
  }

  async function loadActivity(id: number) {
    setActivityLoading(true);
    try {
      const response = await api(`${API}/${id}/activities`);
      setActivity(await response.json() as LeadActivity[]);
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unauthorized') setError('Could not load activity history.');
    } finally {
      setActivityLoading(false);
    }
  }

  function openLead(lead: Lead) {
    setSelectedId(lead.id);
    window.requestAnimationFrame(() => {
      document.getElementById('lead-detail')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
    setNote(lead.note ?? '');
    setActivity([]);
    void loadActivity(lead.id);
    setError('');
    setSuccess('');
  }

  if (restoringSession) return <main className="login-page"><section className="login-card"><h1>Nexora</h1><p>Restoring local session…</p></section></main>;

  if (!auth) return (
    <main className="login-page">
      <section className="login-card">
        <div className="brand login-brand">◆ Nexora <small>Business platform</small></div>
        <h1>Welcome back</h1>
        <p>Sign in to your local workspace.</p>
        <form onSubmit={signIn}>
          <label><span>Username</span><input required value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" /></label>
          <label><span>Password</span><input required type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" /></label>
          <button disabled={loading} type="submit">{loading ? 'Signing in…' : 'Sign in'}</button>
        </form>
        {authError && <p role="alert" className="error">{authError}</p>}
        <small>Local development only · Do not use real customer data</small>
      </section>
    </main>
  );

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">◆ Nexora <small>Business platform</small></div>
        <nav aria-label="Main navigation">
          <span className="nav-group">WORKSPACE</span>
          <a className="active" href="#overview">Overview</a>
          <a href="#pipeline">Lead pipeline</a>
          <a href="#tasks">Follow-ups</a>
          <a href="#analytics">Analytics</a>
          <span className="nav-group">ACCOUNT</span>
          {canEdit && <a href="#team">Team members</a>}
          {canEdit && <a href="#security-history">Security history</a>}
          <a href="#account-security">Account security</a>
          <span className="planned">Automations · planned</span>
        </nav>
        <small className="sidebar-foot">Local development · CRM MVP</small>
      </aside>
      <main id="overview">
        <header>
          <div>
            <span className="eyebrow">NEXORA / WORKSPACE OVERVIEW</span>
            <h1>Business overview</h1>
            <p>Organize opportunities, follow up and manage your pipeline.</p>
            <p className="account-caption">{account?.username} · {account?.role === 'ADMIN' ? 'Administrator' : 'Read-only member'} · {account?.organizationId}</p>
          </div>
          <div className="header-actions">
            <span className="workspace-pill"><span className="status-dot" aria-hidden="true" /> Local workspace</span>
            <button type="button" className="sign-out" onClick={() => void signOut()}>Sign out</button>
          </div>
        </header>
        <section className="metrics" aria-label="Pipeline summary">
          <article><small>Total leads</small><strong>{leads.length}</strong><span className="metric-foot">All opportunities</span></article>
          <article><small>In progress</small><strong>{leads.filter(l => l.status === 'CONTACTED' || l.status === 'QUALIFIED').length}</strong><span className="metric-foot">Contacted & qualified</span></article>
          <article><small>Won</small><strong>{leads.filter(l => l.status === 'WON').length}</strong><span className="metric-foot">Closed successfully</span></article>
        </section>
        <section id="analytics" className="panel analytics-panel" aria-label="CRM analytics">
          <div className="panel-header">
            <h2>Pipeline analytics</h2><span>Current data · all leads</span>
          </div>
          <div className="analytics-summary">
            <article><small>Lead conversion</small><strong>{conversionRate}%</strong><span>Won / all leads</span></article>
            <article><small>Win rate</small><strong>{winRate}%</strong><span>Won / won + lost</span></article>
            <article><small>Task completion</small><strong>{taskCompletionRate}%</strong><span>{doneCount} of {tasks.length} done</span></article>
            <article><small>Overdue follow-ups</small><strong className={overdue ? 'overdue' : ''}>{overdue}</strong><span>Open tasks past due date</span></article>
          </div>
          <h3>Leads by status</h3>
          <div className="status-chart">
            {pipelineCounts.map(({ status, count }) => {
              const percent = leads.length ? count / leads.length * 100 : 0;
              return <div className="status-chart-row" key={status}>
                <div className="status-chart-label"><span>{labels[status]}</span><strong>{count}</strong></div>
                <div className="status-track" role="meter" aria-label={`${labels[status]} leads`} aria-valuemin={0} aria-valuemax={leads.length || 1} aria-valuenow={count}>
                  <div className={`status-fill status-${status.toLowerCase()}`} style={{ width: `${percent}%` }} />
                </div>
              </div>;
            })}
          </div>
          <p className="analytics-caption">Snapshot of the leads and tasks currently loaded in this workspace. No historical trends or revenue estimates are calculated.</p>
        </section>
        {canEdit && <section id="create-lead" className="panel lead-create-panel">
          <div className="panel-header"><h2>Add a lead</h2><span>New opportunity</span></div>
          <form onSubmit={add}>
            <label><span>Customer name</span><input required maxLength={120} value={name} onChange={e => setName(e.target.value)} placeholder="Jane Smith" /></label>
            <label><span>Email</span><input required type="email" maxLength={254} value={email} onChange={e => setEmail(e.target.value)} placeholder="jane@example.com" /></label>
            <button disabled={saving} type="submit">Add lead</button>
          </form>
        </section>}
        <section id="pipeline" className="panel">
          <div className="panel-header"><h2>Lead pipeline</h2><div className="panel-actions"><span>{filtered.length} of {leads.length} records</span>{canEdit && <button type="button" className="secondary-button export-button" onClick={() => void downloadCsv('leads')}>Export leads CSV</button>}</div></div>
          <div className="toolbar">
            <label><span>Search leads</span><input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Name, email or notes" /></label>
            <label><span>Filter by status</span><select value={filter} onChange={e => setFilter(e.target.value as StatusFilter)}>
              <option value="ALL">All statuses</option>
              {statuses.map(status => <option key={status} value={status}>{labels[status]}</option>)}
            </select></label>
            <button className="secondary-button" type="button" onClick={() => { setSearch(''); setFilter('ALL'); }}>Clear filters</button>
          </div>
          {error && <p role="alert" className="error">{error}</p>}
          {success && <p role="status" className="success">{success}</p>}
          {leads.length === 0 ? <p className="empty">No leads yet. Add your first customer above.</p> :
            filtered.length === 0 ? <p className="empty">No matching leads. Try changing your filters.</p> :
            <div className="table-wrap"><table>
              <thead><tr><th>Customer</th><th>Email</th><th>Status</th><th>Details</th></tr></thead>
              <tbody>{filtered.map(lead => <tr key={lead.id}>
                <td><strong>{lead.name}</strong></td><td>{lead.email}</td>
                <td>{canEdit ? <select disabled={saving} aria-label={`Status for ${lead.name}`} value={lead.status} onChange={e => void changeStatus(lead.id, e.target.value as Status)}>
                  {statuses.map(status => <option key={status} value={status}>{labels[status]}</option>)}
                </select> : <span>{labels[lead.status]}</span>}</td>
                <td><button type="button" className="secondary-button details-button" onClick={() => openLead(lead)}>View</button></td>
              </tr>)}</tbody>
            </table></div>}
        </section>
        <section id="tasks" className="panel">
          <div className="panel-header"><h2>Follow-up tasks</h2><div className="panel-actions"><span>{overdue} overdue · {upcoming} upcoming</span>{canEdit && <button type="button" className="secondary-button export-button" onClick={() => void downloadCsv('tasks')}>Export tasks CSV</button>}</div></div>
          <div className="task-summary" aria-label="Follow-up priorities">
            <button type="button" className={taskFilter === 'OVERDUE' ? 'task-priority active' : 'task-priority'} aria-pressed={taskFilter === 'OVERDUE'} onClick={() => setTaskFilter('OVERDUE')}>
              <span>Overdue</span><strong>{tasks.filter(task => task.status === 'OPEN' && task.dueDate < localToday()).length}</strong>
            </button>
            <button type="button" className={taskFilter === 'TODAY' ? 'task-priority active' : 'task-priority'} aria-pressed={taskFilter === 'TODAY'} onClick={() => setTaskFilter('TODAY')}>
              <span>Due today</span><strong>{tasks.filter(task => task.status === 'OPEN' && task.dueDate === localToday()).length}</strong>
            </button>
            <button type="button" className={taskFilter === 'UPCOMING' ? 'task-priority active' : 'task-priority'} aria-pressed={taskFilter === 'UPCOMING'} onClick={() => setTaskFilter('UPCOMING')}>
              <span>Later</span><strong>{tasks.filter(task => task.status === 'OPEN' && task.dueDate > localToday()).length}</strong>
            </button>
          </div>
          <div className="toolbar task-toolbar">
            <label><span>Show tasks</span><select value={taskFilter} onChange={e => setTaskFilter(e.target.value as 'OPEN' | 'ALL' | 'DONE' | 'OVERDUE' | 'TODAY' | 'UPCOMING')}>
              <option value="OPEN">Open</option><option value="OVERDUE">Overdue</option><option value="TODAY">Due today</option><option value="UPCOMING">Later</option><option value="DONE">Completed</option><option value="ALL">All</option>
            </select></label>
            <label><span>Search follow-ups</span><input type="search" value={taskSearch} onChange={e => setTaskSearch(e.target.value)} placeholder="Task, customer or email" /></label>
            <button type="button" className="secondary-button" onClick={() => { setTaskFilter('OPEN'); setTaskSearch(''); }}>Reset task filters</button>
          </div>
          <p className="analytics-caption task-count" role="status">{shownTasks.length} matching task{shownTasks.length === 1 ? '' : 's'} · sorted by due date</p>
          {shownTasks.length === 0 ? <p className="empty">No tasks in this view. Open a lead to create a follow-up.</p> :
            <div className="table-wrap"><table>
              <thead><tr><th>Task</th><th>Customer</th><th>Due</th><th>Status</th></tr></thead>
              <tbody>{shownTasks.map(task => <tr key={task.id}>
                <td><strong>{task.title}</strong></td>
                <td><button type="button" className="task-lead-link" onClick={() => {
                  const customer = leads.find(lead => lead.id === task.leadId);
                  if (customer) openLead(customer);
                }}>{leadName(task.leadId)}</button></td>
                <td><span className={task.status === 'OPEN' && task.dueDate < localToday() ? 'overdue' : ''}>{task.dueDate}{task.status === 'OPEN' && task.dueDate < localToday() ? ' · Overdue' : ''}</span></td>
                <td>{canEdit ? <button disabled={saving} type="button" className="secondary-button" onClick={() => void updateTask(task.id, task.status === 'OPEN' ? 'DONE' : 'OPEN')}>
                  {task.status === 'OPEN' ? 'Mark done' : 'Reopen'}
                </button> : task.status === 'OPEN' ? 'Open' : 'Done'}</td>
              </tr>)}</tbody>
            </table></div>}
        </section>
        <section id="account-security" className="panel" aria-label="Account security">
          <h2>Account security</h2>
          <p>Change your password. You will be signed out after a successful change.</p>
          <form className="password-form" onSubmit={changeMyPassword}>
            <label><span>Current password</span><input type="password" required autoComplete="current-password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} /></label>
            <label><span>New password (12+ characters)</span><input type="password" required minLength={12} maxLength={128} autoComplete="new-password" value={newPassword} onChange={e => setNewPassword(e.target.value)} /></label>
            <label><span>Confirm new password</span><input type="password" required minLength={12} maxLength={128} autoComplete="new-password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} /></label>
            <button disabled={passwordSaving} type="submit">{passwordSaving ? 'Updating…' : 'Change password'}</button>
          </form>
          {passwordMessage && <p role="status" className="error">{passwordMessage}</p>}
        </section>
        {canEdit && <section id="team" className="panel" aria-label="Team directory">
          <div className="panel-header"><h2>Team members</h2><span>{team.length} accounts</span></div>
          <form className="team-create-form" onSubmit={addMember}>
            <label><span>New member username</span><input required minLength={3} maxLength={120} pattern="[a-zA-Z0-9][a-zA-Z0-9._-]*" value={memberUsername} autoComplete="off" onChange={e => setMemberUsername(e.target.value)} placeholder="alex.dev" /></label>
            <label><span>Temporary password (12+ characters)</span><input required type="password" minLength={12} maxLength={128} autoComplete="new-password" value={memberPassword} onChange={e => setMemberPassword(e.target.value)} placeholder="Choose a strong unique password" /></label>
            <button type="submit" disabled={teamSaving}>Create member</button>
          </form>
          {teamMessage && <p role="status" className="analytics-caption">{teamMessage}</p>}
          {team.length === 0 ? <p className="empty">No team accounts loaded.</p> :
          <div className="table-wrap"><table>
            <thead><tr><th>Username</th><th>Role</th><th>Status</th><th>Access</th><th>Recovery</th></tr></thead>
            <tbody>{team.map(member => <tr key={member.id}>
              <td><strong>{member.username}</strong></td>
              <td>{member.role}</td>
              <td>{member.enabled ? 'Active' : 'Disabled'}</td>
              <td>{member.role === 'MEMBER' ?
                <button type="button" disabled={teamSaving} className="secondary-button" onClick={() => void toggleMember(member)}>
                  {member.enabled ? 'Disable' : 'Enable'}
                </button> : '—'}</td>
              <td>{member.role === 'MEMBER' && member.enabled ?
                <button type="button" disabled={teamSaving} className="secondary-button" onClick={() => {
                  setResetMemberId(member.id); setResetAdminPassword(''); setResetNewPassword(''); setTeamMessage('');
                }}>Reset password</button> : '—'}</td>
            </tr>)}</tbody>
          </table></div>}
          {resetMemberId !== null && <form className="team-create-form" onSubmit={resetMemberPassword} aria-label="Reset member password">
            <label><span>Administrator password (confirm identity)</span><input required type="password" autoComplete="current-password" value={resetAdminPassword} onChange={e => setResetAdminPassword(e.target.value)} /></label>
            <label><span>New member password (12+ characters)</span><input required type="password" minLength={12} maxLength={128} autoComplete="new-password" value={resetNewPassword} onChange={e => setResetNewPassword(e.target.value)} /></label>
            <button disabled={teamSaving} type="submit">Confirm password reset</button>
            <button type="button" className="secondary-button" onClick={() => {setResetMemberId(null);setResetAdminPassword('');setResetNewPassword('');}}>Cancel</button>
          </form>}
          <p className="analytics-caption">Local development only. Account recovery requires your administrator password. Share replacements securely; no email is sent.</p>
        </section>}
        {canEdit && <section id="security-history" className="panel" aria-label="Security events">
          <div className="panel-header"><h2>Security history</h2>
            <button type="button" className="secondary-button" onClick={() => void loadSecurityEvents()}>Refresh events</button>
          </div>
          {securityEvents.length === 0 ? <p className="empty">No security events recorded yet.</p> :
          <div className="table-wrap"><table>
            <thead><tr><th>Action</th><th>By</th><th>Account</th><th>Date</th></tr></thead>
            <tbody>{securityEvents.map(event => <tr key={event.id}>
              <td>{event.eventType.replaceAll('_', ' ')}</td>
              <td>{event.actorUsername}</td>
              <td>{event.targetUsername}</td>
              <td>{new Date(event.createdAt).toLocaleString()}</td>
            </tr>)}</tbody>
          </table></div>}
          <p className="analytics-caption">Local development audit history of successful security actions. This is not tamper-proof.</p>
        </section>}
        {selected && <section id="lead-detail" className="panel lead-detail" aria-label="Lead details">
          <div className="panel-header">
            <div><span className="eyebrow">LEAD DETAILS</span><h2>{selected.name}</h2></div>
            <button type="button" className="secondary-button" onClick={() => { setSelectedId(null); setActivity([]); }}>Close</button>
          </div>
          <p><strong>Email:</strong> <a href={`mailto:${selected.email}`}>{selected.email}</a></p>
          <p><strong>Status:</strong> {labels[selected.status]}</p>
          {canEdit ? <form onSubmit={saveNote} className="note-form">
            <label htmlFor="lead-note"><span>Customer notes</span></label>
            <textarea id="lead-note" rows={5} maxLength={2000} value={note} onChange={e => setNote(e.target.value)} placeholder="Follow-up details, customer needs, next steps…" />
            <div className="note-actions"><small>{note.length} / 2000</small><button disabled={saving || note === (selected.note ?? '')} type="submit">Save note</button></div>
          </form> : <section className="read-only-note"><h3>Customer notes</h3><p>{selected.note || 'No notes yet.'}</p></section>}
          {canEdit && <div className="follow-up-form">
            <h3>New follow-up</h3>
            <form onSubmit={addTask}>
              <label><span>Task</span><input required maxLength={160} value={taskTitle} onChange={e => setTaskTitle(e.target.value)} placeholder="Call customer" /></label>
              <label><span>Due date</span><input required type="date" value={taskDue} onChange={e => setTaskDue(e.target.value)} /></label>
              <button disabled={saving} type="submit">Add task</button>
            </form>
          </div>}
          <div className="activity-history">
            <h3>Recent activity</h3>
            {activityLoading ? <p className="empty">Loading activity…</p> : activity.length === 0 ?
              <p className="empty">No recorded activity yet.</p> :
              <ol>{activity.map(item => <li key={item.id}>
                <div><strong>{item.summary}</strong><small>{new Date(item.createdAt).toLocaleString()}</small></div>
              </li>)}</ol>}
          </div>
        </section>}
      </main>
    </div>
  );
}

createRoot(document.getElementById('root')!).render(<App />);
