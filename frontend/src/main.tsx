import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

type Account = { username: string; organizationId: string; role: 'ADMIN' | 'MEMBER' };
type TeamMember = { id: number; username: string; role: string; enabled: boolean; createdAt: string };
type Status = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'WON' | 'LOST';
type Lead = { id: number; name: string; email: string; note: string | null; status: Status };
type StatusFilter = Status | 'ALL';
type LeadTask = { id: number; leadId: number; title: string; dueDate: string; status: 'OPEN' | 'DONE' };
type LeadActivity = { id: number; kind: 'CREATED' | 'STATUS_CHANGED' | 'NOTE_UPDATED'; summary: string; createdAt: string };
const API = 'http://localhost:8080/api/leads';
const TASK_API = 'http://localhost:8080/api/tasks';
function localToday() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; }
const statuses: Status[] = ['NEW', 'CONTACTED', 'QUALIFIED', 'WON', 'LOST'];
const labels: Record<Status, string> = {
  NEW: 'New', CONTACTED: 'Contacted', QUALIFIED: 'Qualified', WON: 'Won', LOST: 'Lost'
};

function App() {
  const [auth, setAuth] = useState('');
  const [account, setAccount] = useState<Account | null>(null);
  const [team, setTeam] = useState<TeamMember[]>([]);
  const [memberUsername, setMemberUsername] = useState('');
  const [memberPassword, setMemberPassword] = useState('');
  const [teamSaving, setTeamSaving] = useState(false);
  const [teamMessage, setTeamMessage] = useState('');
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
  const [taskFilter, setTaskFilter] = useState<'OPEN' | 'ALL' | 'DONE'>('OPEN');
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
  const shownTasks = useMemo(() => tasks.filter(task => taskFilter === 'ALL' || task.status === taskFilter), [tasks, taskFilter]);
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

  function signOut() {
    setAuth('');
    setAccount(null);
    setTeam([]);
    setMemberUsername('');
    setMemberPassword('');
    setTeamMessage('');
    setPassword('');
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setPasswordMessage('');
    setLeads([]);
    setTasks([]);
    setSelectedId(null);
    setNote('');
    setActivity([]);
    setError('');
    setSuccess('');
  }

  async function api(url: string, init: RequestInit = {}) {
    const response = await fetch(url, {
      ...init,
      headers: { 'Authorization': `Basic ${auth}`, ...init.headers }
    });
    if (response.status === 401) {
      signOut();
      setAuthError('Your session expired. Please sign in again.');
      throw new Error('Unauthorized');
    }
    if (!response.ok) throw new Error(`Request failed (${response.status})`);
    return response;
  }

  async function signIn(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAuthError('');
    let encoded: string;
    try {
      // Supports non-ASCII credentials; the password remains only in memory.
      encoded = btoa(Array.from(new TextEncoder().encode(`${username}:${password}`),
        byte => String.fromCharCode(byte)).join(''));
    } catch {
      setAuthError('Could not encode these credentials.');
      return;
    }
    setLoading(true);
    try {
      const response = await fetch(API, { headers: { Authorization: `Basic ${encoded}` } });
      if (!response.ok) throw new Error('Invalid credentials');
      const accountResponse = await fetch('http://localhost:8080/api/me', {
        headers: { Authorization: `Basic ${encoded}` }
      });
      if (!accountResponse.ok) throw new Error('Cannot resolve account');
      const currentAccount = await accountResponse.json() as Account;
      setLeads(await response.json() as Lead[]);
      setAccount(currentAccount);
      setAuth(encoded);
      setPassword('');
      setError('');
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
      const response = await api('http://localhost:8080/api/team');
      setTeam(await response.json() as TeamMember[]);
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unauthorized') setError('Could not load team members.');
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
      const response = await api('http://localhost:8080/api/me/password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ currentPassword, newPassword })
      });
      if (!response.ok) throw new Error('Password change failed');
      signOut();
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
      const response = await api('http://localhost:8080/api/team', {
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
      await api(`http://localhost:8080/api/team/${member.id}/enabled`, {
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

  useEffect(() => { if (auth) { void load(); void loadTasks(); } }, [auth]);
  useEffect(() => { if (auth && canEdit) void loadTeam(); }, [auth, canEdit]);

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
    setNote(lead.note ?? '');
    setActivity([]);
    void loadActivity(lead.id);
    setError('');
    setSuccess('');
  }

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
          <span className="active">Overview</span>
          <span className="active-section">Lead workspace</span>
          <span className="active-section">Analytics</span>
          {canEdit && <span className="active-section">Team</span>}
          <span className="planned">Automations · planned</span>
        </nav>
        <small className="sidebar-foot">Local development · CRM MVP</small>
      </aside>
      <main>
        <header>
          <div>
            <span className="eyebrow">WORKSPACE / CRM</span>
            <h1>Business overview</h1>
            <p>Organize opportunities, follow up and manage your pipeline.</p>
            <p className="account-caption">{account?.username} · {account?.role === 'ADMIN' ? 'Administrator' : 'Read-only member'} · {account?.organizationId}</p>
          </div>
          <button type="button" className="sign-out" onClick={signOut}>Sign out</button>
        </header>
        <section className="metrics" aria-label="Pipeline summary">
          <article><small>Total leads</small><strong>{leads.length}</strong></article>
          <article><small>In progress</small><strong>{leads.filter(l => l.status === 'CONTACTED' || l.status === 'QUALIFIED').length}</strong></article>
          <article><small>Won</small><strong>{leads.filter(l => l.status === 'WON').length}</strong></article>
        </section>
        <section className="panel analytics-panel" aria-label="CRM analytics">
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
        {canEdit && <section className="panel">
          <h2>Add a lead</h2>
          <form onSubmit={add}>
            <label><span>Customer name</span><input required maxLength={120} value={name} onChange={e => setName(e.target.value)} placeholder="Jane Smith" /></label>
            <label><span>Email</span><input required type="email" maxLength={254} value={email} onChange={e => setEmail(e.target.value)} placeholder="jane@example.com" /></label>
            <button disabled={saving} type="submit">Add lead</button>
          </form>
        </section>}
        <section className="panel">
          <div className="panel-header"><h2>Lead pipeline</h2><span>{filtered.length} of {leads.length} records</span></div>
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
        <section className="panel">
          <div className="panel-header"><h2>Follow-up tasks</h2><span>{overdue} overdue · {upcoming} upcoming</span></div>
          <div className="toolbar">
            <label><span>Show tasks</span><select value={taskFilter} onChange={e => setTaskFilter(e.target.value as 'OPEN' | 'ALL' | 'DONE')}>
              <option value="OPEN">Open</option><option value="ALL">All</option><option value="DONE">Completed</option>
            </select></label>
          </div>
          {shownTasks.length === 0 ? <p className="empty">No tasks in this view. Open a lead to create a follow-up.</p> :
            <div className="table-wrap"><table>
              <thead><tr><th>Task</th><th>Customer</th><th>Due</th><th>Status</th></tr></thead>
              <tbody>{shownTasks.map(task => <tr key={task.id}>
                <td><strong>{task.title}</strong></td>
                <td>{leadName(task.leadId)}</td>
                <td><span className={task.status === 'OPEN' && task.dueDate < localToday() ? 'overdue' : ''}>{task.dueDate}{task.status === 'OPEN' && task.dueDate < localToday() ? ' · Overdue' : ''}</span></td>
                <td>{canEdit ? <button disabled={saving} type="button" className="secondary-button" onClick={() => void updateTask(task.id, task.status === 'OPEN' ? 'DONE' : 'OPEN')}>
                  {task.status === 'OPEN' ? 'Mark done' : 'Reopen'}
                </button> : task.status === 'OPEN' ? 'Open' : 'Done'}</td>
              </tr>)}</tbody>
            </table></div>}
        </section>
        <section className="panel" aria-label="Account security">
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
        {canEdit && <section className="panel" aria-label="Team directory">
          <div className="panel-header"><h2>Team members</h2><span>{team.length} accounts</span></div>
          <form className="team-create-form" onSubmit={addMember}>
            <label><span>New member username</span><input required minLength={3} maxLength={120} pattern="[a-zA-Z0-9][a-zA-Z0-9._-]*" value={memberUsername} autoComplete="off" onChange={e => setMemberUsername(e.target.value)} placeholder="alex.dev" /></label>
            <label><span>Temporary password (12+ characters)</span><input required type="password" minLength={12} maxLength={128} autoComplete="new-password" value={memberPassword} onChange={e => setMemberPassword(e.target.value)} placeholder="Choose a strong unique password" /></label>
            <button type="submit" disabled={teamSaving}>Create member</button>
          </form>
          {teamMessage && <p role="status" className="analytics-caption">{teamMessage}</p>}
          {team.length === 0 ? <p className="empty">No team accounts loaded.</p> :
          <div className="table-wrap"><table>
            <thead><tr><th>Username</th><th>Role</th><th>Status</th><th>Access</th></tr></thead>
            <tbody>{team.map(member => <tr key={member.id}>
              <td><strong>{member.username}</strong></td>
              <td>{member.role}</td>
              <td>{member.enabled ? 'Active' : 'Disabled'}</td>
              <td>{member.role === 'MEMBER' ?
                <button type="button" disabled={teamSaving} className="secondary-button" onClick={() => void toggleMember(member)}>
                  {member.enabled ? 'Disable' : 'Enable'}
                </button> : '—'}</td>
            </tr>)}</tbody>
          </table></div>}
          <p className="analytics-caption">Local development only. New accounts have MEMBER read-only access. Share passwords securely; no invitation emails are sent.</p>
        </section>}
        {selected && <section className="panel lead-detail" aria-label="Lead details">
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
