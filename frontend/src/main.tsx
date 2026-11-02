import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

type Status = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'WON' | 'LOST';
type Lead = { id: number; name: string; email: string; note: string | null; status: Status };
const API = 'http://localhost:8080/api/leads';
const statuses: Status[] = ['NEW', 'CONTACTED', 'QUALIFIED', 'WON', 'LOST'];

function App() {
  const [leads, setLeads] = useState<Lead[]>([]);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  async function load() {
    try {
      const response = await fetch(API);
      if (!response.ok) throw new Error('Cannot load leads');
      setLeads(await response.json() as Lead[]);
      setError('');
    } catch {
      setError('Cannot connect to backend. Start PostgreSQL and Spring Boot.');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void load(); }, []);

  async function add(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      const response = await fetch(API, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, email, status: 'NEW', note: '' })
      });
      if (!response.ok) throw new Error('Cannot add lead');
      setName('');
      setEmail('');
      await load();
    } catch {
      setError('Could not create lead.');
    }
  }

  async function changeStatus(id: number, status: Status) {
    try {
      const response = await fetch(`${API}/${id}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status })
      });
      if (!response.ok) throw new Error('Cannot update lead');
      await load();
    } catch {
      setError('Could not update status.');
    }
  }

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">◆ Nexora <small>Business platform</small></div>
        <nav aria-label="Main navigation">
          <span className="active">Overview</span>
          <span>Leads</span>
          <span className="planned">Analytics · planned</span>
          <span className="planned">Automations · planned</span>
        </nav>
        <small className="sidebar-foot">Local development · MVP 0.1</small>
      </aside>
      <main>
        <header>
          <div>
            <span className="eyebrow">WORKSPACE / OVERVIEW</span>
            <h1>Business overview</h1>
            <p>Track opportunities and manage your customer pipeline.</p>
          </div>
          <span className="avatar" aria-label="Demo user">AZ</span>
        </header>
        <section className="metrics" aria-label="Pipeline summary">
          <article><small>Total leads</small><strong>{leads.length}</strong></article>
          <article><small>In progress</small><strong>{leads.filter(l => l.status === 'CONTACTED' || l.status === 'QUALIFIED').length}</strong></article>
          <article><small>Won</small><strong>{leads.filter(l => l.status === 'WON').length}</strong></article>
        </section>
        <section className="panel">
          <h2>Add a lead</h2>
          <form onSubmit={add}>
            <label><span>Customer name</span><input required value={name} onChange={e => setName(e.target.value)} placeholder="Jane Smith" /></label>
            <label><span>Email</span><input required type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="jane@example.com" /></label>
            <button type="submit">Add lead</button>
          </form>
        </section>
        <section className="panel">
          <div className="panel-header"><h2>Lead pipeline</h2><span>{leads.length} records</span></div>
          {error && <p role="alert" className="error">{error}</p>}
          {loading ? <p className="empty">Loading...</p> : leads.length === 0 ? <p className="empty">No leads yet. Add your first customer above.</p> :
            <div className="table-wrap"><table>
              <thead><tr><th>Customer</th><th>Email</th><th>Status</th></tr></thead>
              <tbody>{leads.map(lead => <tr key={lead.id}>
                <td><strong>{lead.name}</strong></td>
                <td>{lead.email}</td>
                <td><select aria-label={`Status for ${lead.name}`} value={lead.status} onChange={e => void changeStatus(lead.id, e.target.value as Status)}>
                  {statuses.map(status => <option key={status} value={status}>{status}</option>)}
                </select></td>
              </tr>)}</tbody>
            </table></div>}
        </section>
      </main>
    </div>
  );
}

createRoot(document.getElementById('root')!).render(<App />);
