import type { Task } from '../api/models';

export function TaskCard({ task, onOpen }: { task: Task; onOpen: (t: Task) => void }) {
  return (
    <button
      onClick={() => onOpen(task)}
      className="w-full rounded-lg border border-slate-200 bg-white p-3 text-left shadow-sm transition hover:shadow dark:border-slate-700 dark:bg-slate-900"
      data-testid={`task-${task.taskKey}`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-mono text-slate-500">{task.taskKey}</span>
        <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[11px] font-medium text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300">
          {task.priority}
        </span>
      </div>
      <div className="mt-1 text-sm font-medium">{task.title}</div>
      {task.dueDate ? <div className="mt-1 text-xs text-slate-500">Due {new Date(task.dueDate).toLocaleDateString()}</div> : null}
    </button>
  );
}
