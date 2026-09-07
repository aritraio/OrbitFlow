import { useEffect, useState } from 'react';
import { membersApi, type Member } from '../api/models';

const ROLES = ['ADMIN', 'MEMBER', 'GUEST'] as const;

export function WorkspaceMembersModal({ workspaceId, onClose }: { workspaceId: string; onClose: () => void }) {
  const [members, setMembers] = useState<Member[]>([]);
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<string>('MEMBER');
  const [error, setError] = useState<string | null>(null);

  const reload = async () => {
    const list = await membersApi.list(workspaceId).catch(() => [] as Member[]);
    setMembers(list);
  };

  useEffect(() => {
    void reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId]);

  const invite = async () => {
    setError(null);
    try {
      await membersApi.invite(workspaceId, email.trim(), role);
      setEmail('');
      void reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Invite failed');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-5 dark:bg-slate-900" data-testid="members-modal">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">Workspace members</h2>
          <button onClick={onClose} className="rounded px-2 py-1 text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800">
            ✕
          </button>
        </div>
        <div className="mt-3 space-y-1">
          {members.map((m) => (
            <div key={m.userId} className="flex items-center justify-between rounded border px-2 py-1.5 text-sm dark:border-slate-700">
              <span>
                <span className="font-medium">@{m.username}</span>
                <span className="ml-2 text-xs text-slate-500">{m.email}</span>
              </span>
              <span className="rounded bg-slate-100 px-2 py-0.5 text-[11px] font-bold dark:bg-slate-800">{m.role}</span>
            </div>
          ))}
          {members.length === 0 ? <div className="text-xs text-slate-500">No members found.</div> : null}
        </div>
        <div className="mt-4">
          <h3 className="text-sm font-semibold">Invite by email</h3>
          <div className="mt-1 flex gap-2">
            <input
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="teammate@example.com"
              className="flex-1 rounded border px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
            />
            <select value={role} onChange={(e) => setRole(e.target.value)} className="rounded border px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-800">
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
            <button onClick={invite} className="rounded bg-indigo-600 px-3 py-1.5 text-sm text-white">
              Invite
            </button>
          </div>
          {error ? <div className="mt-1 text-xs text-red-600">{error}</div> : null}
        </div>
      </div>
    </div>
  );
}
