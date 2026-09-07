import { useEffect, useState } from 'react';
import { reportsApi, type Milestone, type ProjectReport } from '../api/models';

export function ReportsView({ projectId }: { projectId: string }) {
  const [report, setReport] = useState<ProjectReport | null>(null);
  const [milestones, setMilestones] = useState<Milestone[]>([]);
  const [name, setName] = useState('');

  const reload = async () => {
    const [r, m] = await Promise.all([
      reportsApi.report(projectId).catch(() => null),
      reportsApi.milestones(projectId).catch(() => [] as Milestone[]),
    ]);
    setReport(r);
    setMilestones(m);
  };

  useEffect(() => {
    void reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const create = async () => {
    if (!name.trim()) return;
    await reportsApi.createMilestone(projectId, name.trim());
    setName('');
    void reload();
  };

  const complete = async (id: string) => {
    await reportsApi.completeMilestone(id);
    void reload();
  };

  if (!report) return <div className="p-6 text-sm text-slate-500">Loading report…</div>;

  return (
    <div className="max-w-4xl p-4" data-testid="reports-view">
      <div className="text-xs text-slate-500">
        Generated {new Date(report.generatedAt).toLocaleString()} · {report.timezone} · archived tasks excluded
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
        {[
          ['Total', report.total],
          ['Completed', report.completed],
          ['Overdue', report.overdue],
          ['Blocked', report.blocked],
        ].map(([label, value]) => (
          <div key={label as string} className="rounded-xl border p-3 dark:border-slate-700">
            <div className="text-2xl font-bold">{value}</div>
            <div className="text-xs text-slate-500">{label}</div>
          </div>
        ))}
      </div>
      <div className="mt-2 rounded-xl border p-3 text-sm dark:border-slate-700">
        Avg cycle time: <strong>{report.avgCycleHours.toFixed(1)}h</strong>
      </div>
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <div className="rounded-xl border p-3 dark:border-slate-700">
          <h4 className="text-sm font-semibold">By status</h4>
          {Object.entries(report.byStatus).map(([k, v]) => (
            <div key={k} className="flex justify-between text-sm">
              <span>{k}</span>
              <span>{v}</span>
            </div>
          ))}
        </div>
        <div className="rounded-xl border p-3 dark:border-slate-700">
          <h4 className="text-sm font-semibold">By priority</h4>
          {Object.entries(report.byPriority).map(([k, v]) => (
            <div key={k} className="flex justify-between text-sm">
              <span>{k}</span>
              <span>{v}</span>
            </div>
          ))}
        </div>
      </div>
      <div className="mt-4">
        <h4 className="text-sm font-semibold">Milestones</h4>
        <div className="mt-2 flex gap-2">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="New milestone name"
            className="flex-1 rounded border px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
          />
          <button onClick={create} className="rounded bg-indigo-600 px-3 py-1.5 text-sm text-white">
            Create
          </button>
        </div>
        <div className="mt-2 space-y-1">
          {milestones.map((m) => (
            <div key={m.id} className="flex items-center justify-between rounded border px-2 py-1.5 text-sm dark:border-slate-700">
              <span>
                {m.name}
                {m.completedAt ? <span className="ml-2 text-xs text-green-700">completed</span> : null}
              </span>
              {!m.completedAt ? (
                <button onClick={() => void complete(m.id)} className="text-xs underline">
                  Complete milestone
                </button>
              ) : null}
            </div>
          ))}
          {milestones.length === 0 ? <div className="text-xs text-slate-500">No milestones yet.</div> : null}
        </div>
      </div>
    </div>
  );
}
