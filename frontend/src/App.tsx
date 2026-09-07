import { useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route, Link, useNavigate, useParams, Navigate, NavLink } from 'react-router-dom';
import { AuthProvider, useAuth } from './hooks/useAuth';
import { workspacesApi, projectsApi, type Workspace, type Project } from './api/models';
import { KanbanBoard } from './components/KanbanBoard';
import { GlobalSearch } from './components/GlobalSearch';
import { DocumentWiki } from './components/DocumentWiki';
import { ReportsView } from './components/ReportsView';
import { WorkspaceMembersModal } from './components/WorkspaceMembersModal';

function LoginPage() {
  const { login, register } = useAuth();
  const nav = useNavigate();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      if (mode === 'login') await login(email, password);
      else await register(email, username, password, username);
      nav('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Auth failed');
    }
  };

  return (
    <div className="mx-auto mt-24 max-w-sm rounded-xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-slate-900">
      <h1 className="text-xl font-bold">OrbitFlow</h1>
      <p className="text-sm text-slate-500">Collaborative Project Hub</p>
      <form onSubmit={submit} className="mt-4 flex flex-col gap-2">
        <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Email" className="rounded border px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-800" />
        {mode === 'register' ? (
          <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="Username" className="rounded border px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-800" />
        ) : null}
        <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" placeholder="Password" className="rounded border px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-800" />
        {error ? <div className="text-sm text-red-600">{error}</div> : null}
        <button className="rounded bg-indigo-600 px-3 py-2 text-sm text-white">{mode === 'login' ? 'Log in' : 'Create account'}</button>
      </form>
      <button className="mt-2 text-xs underline" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
        {mode === 'login' ? 'Need an account? Register' : 'Have an account? Log in'}
      </button>
    </div>
  );
}

function WorkspaceSwitcher({ workspaces, active, onPick }: { workspaces: Workspace[]; active: string | null; onPick: (id: string) => void }) {
  return (
    <select value={active ?? ''} onChange={(e) => onPick(e.target.value)} className="rounded border px-2 py-1 text-sm dark:border-slate-700 dark:bg-slate-800">
      <option value="" disabled>
        Select workspace
      </option>
      {workspaces.map((w) => (
        <option key={w.id} value={w.id}>
          {w.name} ({w.role})
        </option>
      ))}
    </select>
  );
}

function HomePage() {
  const { tokens, logout } = useAuth();
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [wsId, setWsId] = useState<string | null>(() => localStorage.getItem('orbitflow.workspaceId'));
  const [membersOpen, setMembersOpen] = useState(false);

  useEffect(() => {
    workspacesApi.list().then((ws) => {
      setWorkspaces(ws);
      if (!wsId && ws.length > 0) {
        setWsId(ws[0].id);
        localStorage.setItem('orbitflow.workspaceId', ws[0].id);
      }
    }).catch(() => undefined);
  }, [wsId]);

  useEffect(() => {
    if (wsId) projectsApi.list(wsId).then(setProjects).catch(() => setProjects([]));
  }, [wsId]);

  if (!tokens) return <Navigate to="/login" replace />;

  return (
    <div className="mx-auto max-w-6xl p-4">
      <header className="flex items-center justify-between gap-2">
        <h1 className="text-xl font-bold">OrbitFlow</h1>
        <div className="flex items-center gap-2">
          <GlobalSearch />
          <WorkspaceSwitcher workspaces={workspaces} active={wsId} onPick={(id) => { setWsId(id); localStorage.setItem('orbitflow.workspaceId', id); }} />
          {wsId ? (
            <button onClick={() => setMembersOpen(true)} className="rounded border px-2 py-1 text-xs dark:border-slate-700">
              Members
            </button>
          ) : null}
          <span className="text-xs text-slate-500">@{tokens.username}</span>
          <button onClick={logout} className="text-xs underline">
            Logout
          </button>
        </div>
      </header>
      {membersOpen && wsId ? <WorkspaceMembersModal workspaceId={wsId} onClose={() => setMembersOpen(false)} /> : null}
      <div className="mt-4 grid gap-2">
        {projects.map((p) => (
          <Link key={p.id} to={`/projects/${p.id}`} className="rounded-lg border p-3 hover:shadow dark:border-slate-700">
            <span className="font-mono text-xs text-slate-500">{p.key}</span>
            <div className="font-medium">{p.name}</div>
          </Link>
        ))}
        {projects.length === 0 ? <div className="text-sm text-slate-500">No projects yet.</div> : null}
      </div>
    </div>
  );
}

function ProjectPage() {
  const { projectId } = useParams();
  const { tokens } = useAuth();
  if (!tokens) return <Navigate to="/login" replace />;
  if (!projectId) return <div>Missing project</div>;
  return (
    <div>
      <div className="flex items-center gap-4 p-4 pb-0">
        <Link to="/" className="text-sm underline">
          ← All projects
        </Link>
        <nav className="flex gap-1 text-sm">
          <NavLink to={`/projects/${projectId}`} end className={({ isActive }) => `rounded px-2 py-1 ${isActive ? 'bg-slate-900 text-white dark:bg-white dark:text-slate-900' : 'underline'}`}>
            Board
          </NavLink>
          <NavLink to={`/projects/${projectId}/docs`} className={({ isActive }) => `rounded px-2 py-1 ${isActive ? 'bg-slate-900 text-white dark:bg-white dark:text-slate-900' : 'underline'}`}>
            Docs
          </NavLink>
          <NavLink to={`/projects/${projectId}/reports`} className={({ isActive }) => `rounded px-2 py-1 ${isActive ? 'bg-slate-900 text-white dark:bg-white dark:text-slate-900' : 'underline'}`}>
            Reports
          </NavLink>
        </nav>
        <span className="ml-auto">
          <GlobalSearch projectId={projectId} />
        </span>
      </div>
      <Routes>
        <Route index element={<KanbanBoard projectId={projectId} />} />
        <Route path="docs" element={<DocumentWiki projectId={projectId} />} />
        <Route path="reports" element={<ReportsView projectId={projectId} />} />
      </Routes>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<HomePage />} />
          <Route path="/projects/:projectId/*" element={<ProjectPage />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
