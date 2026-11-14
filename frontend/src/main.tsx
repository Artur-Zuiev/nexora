import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

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
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [authError, setAuthError] = useState('');
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

  const selected = leads.find(lead => lead.id === selectedId) ?? null;
  const shownTasks = useMemo(() => tasks.filter(task => taskFilter === 'ALL' || task.status === taskFilter), [tasks, taskFilter]);
  const overdue = tasks.filter(task => task.status === 'OPEN' && task.dueDate < localToday()).length;
  const upcoming = tasks.filter(task => task.status === 'OPEN' && task.dueDate >= localToday()).length;
  const leadName = (id: number) => leads.find(lead => lead.id === id)?.name ?? `Lead #${id}`;
  const filtered = useMemo(() => leads.filter(lead => {
    const q = search.trim().toLocaleLowerCase();
    const matchesSearch = !q || [lead.name, lead.email, lead.note ?? '']
      .some(value => value.toLocaleLowerCase().includes(q));
    return matchesSearch && (filter === 'ALL' || lead.status === filter);
  }), [leads, search, filter]);

  function signOut() {
    setAuth('');
    setPassword('');
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
      setLeads(await response.json() as Lead[]);
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

  useEffect(() => { if (auth) { void load(); void loadTasks(); } }, [auth]);

  async function addTask(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
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
          <span className="planned">Analytics · planned</span>
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
          </div>
          <button type="button" className="sign-out" onClick={signOut}>Sign out</button>
        </header>
        <section className="metrics" aria-label="Pipeline summary">
          <article><small>Total leads</small><strong>{leads.length}</strong></article>
          <article><small>In progress</small><strong>{leads.filter(l => l.status === 'CONTACTED' || l.status === 'QUALIFIED').length}</strong></article>
          <article><small>Won</small><strong>{leads.filter(l => l.status === 'WON').length}</strong></article>
        </section>
        <section className="panel">
          <h2>Add a lead</h2>
          <form onSubmit={add}>
            <label><span>Customer name</span><input required maxLength={120} value={name} onChange={e => setName(e.target.value)} placeholder="Jane Smith" /></label>
            <label><span>Email</span><input required type="email" maxLength={254} value={email} onChange={e => setEmail(e.target.value)} placeholder="jane@example.com" /></label>
            <button disabled={saving} type="submit">Add lead</button>
          </form>
        </section>
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
                <td><select disabled={saving} aria-label={`Status for ${lead.name}`} value={lead.status} onChange={e => void changeStatus(lead.id, e.target.value as Status)}>
                  {statuses.map(status => <option key={status} value={status}>{labels[status]}</option>)}
                </select></td>
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
                <td><button disabled={saving} type="button" className="secondary-button" onClick={() => void updateTask(task.id, task.status === 'OPEN' ? 'DONE' : 'OPEN')}>
                  {task.status === 'OPEN' ? 'Mark done' : 'Reopen'}
                </button></td>
              </tr>)}</tbody>
            </table></div>}
        </section>
        {selected && <section className="panel lead-detail" aria-label="Lead details">
          <div className="panel-header">
            <div><span className="eyebrow">LEAD DETAILS</span><h2>{selected.name}</h2></div>
            <button type="button" className="secondary-button" onClick={() => { setSelectedId(null); setActivity([]); }}>Close</button>
          </div>
          <p><strong>Email:</strong> <a href={`mailto:${selected.email}`}>{selected.email}</a></p>
          <p><strong>Status:</strong> {labels[selected.status]}</p>
          <form onSubmit={saveNote} className="note-form">
            <label htmlFor="lead-note"><span>Customer notes</span></label>
            <textarea id="lead-note" rows={5} maxLength={2000} value={note} onChange={e => setNote(e.target.value)} placeholder="Follow-up details, customer needs, next steps…" />
            <div className="note-actions"><small>{note.length} / 2000</small><button disabled={saving || note === (selected.note ?? '')} type="submit">Save note</button></div>
          </form>
          <div className="follow-up-form">
            <h3>New follow-up</h3>
            <form onSubmit={addTask}>
              <label><span>Task</span><input required maxLength={160} value={taskTitle} onChange={e => setTaskTitle(e.target.value)} placeholder="Call customer" /></label>
              <label><span>Due date</span><input required type="date" value={taskDue} onChange={e => setTaskDue(e.target.value)} /></label>
              <button disabled={saving} type="submit">Add task</button>
            </form>
          </div>
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
