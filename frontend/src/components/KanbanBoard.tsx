import { useCallback, useEffect, useState } from 'react';
import { DndContext, type DragEndEvent, useDroppable, useDraggable } from '@dnd-kit/core';
import { boardsApi, tasksApi, type Board, type Task } from '../api/models';
import { TaskCard } from './TaskCard';
import { TaskModal } from './TaskModal';
import { PresenceAvatars } from './PresenceAvatars';
import { useWebSocket } from '../hooks/useWebSocket';
import { usePresence } from '../hooks/usePresence';

function Column({ id, title, wipLimit, count, children, overLimit }: { id: string; title: string; wipLimit: number | null; count: number; children: React.ReactNode; overLimit: boolean }) {
  const { setNodeRef } = useDroppable({ id });
  return (
    <div ref={setNodeRef} className={`flex w-72 shrink-0 flex-col rounded-xl bg-slate-100 p-2 dark:bg-slate-900 ${overLimit ? 'ring-2 ring-red-500' : ''}`}>
      <div className="flex items-center justify-between px-2 py-1">
        <span className="text-sm font-semibold">{title}</span>
        <span className="text-xs text-slate-500">
          {count}
          {wipLimit != null ? ` / ${wipLimit}` : ''}
        </span>
      </div>
      <div className="flex flex-col gap-2">{children}</div>
    </div>
  );
}

function DraggableCard({ task, onOpen }: { task: Task; onOpen: (t: Task) => void }) {
  const { attributes, listeners, setNodeRef, transform } = useDraggable({ id: task.id });
  return (
    <div ref={setNodeRef} style={{ transform: transform ? `translate(${transform.x}px, ${transform.y}px)` : undefined }} {...attributes} {...listeners}>
      <TaskCard task={task} onOpen={onOpen} />
    </div>
  );
}

export function KanbanBoard({ projectId }: { projectId: string }) {
  const [board, setBoard] = useState<Board | null>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [selected, setSelected] = useState<Task | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);
  const viewers = usePresence(board?.id ?? null);

  const reload = useCallback(async () => {
    const b = await boardsApi.byProject(projectId);
    setBoard(b);
    const t = await tasksApi.list(projectId);
    setTasks(t);
  }, [projectId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    const onResync = () => void reload();
    window.addEventListener('orbitflow:resync', onResync);
    return () => window.removeEventListener('orbitflow:resync', onResync);
  }, [reload]);

  const { status } = useWebSocket(board?.id ?? null, (evt) => {
    if (evt.eventType === 'TaskMoved' || evt.eventType === 'TaskCreated' || evt.eventType === 'TaskUpdated') {
      void reload(); // simple + correct; optimistic rank update applied on drag
    }
  });

  const onDragEnd = async (e: DragEndEvent) => {
    const taskId = String(e.active.id);
    const destColumnId = e.over ? String(e.over.id) : null;
    if (!destColumnId) return;
    const task = tasks.find((t) => t.id === taskId);
    if (!task) return;
    // Precision LexoRank: compute neighbor cards in the destination column
    // (sorted by rank, excluding the dragged card) around the drop position.
    // Column-level droppable => append at end: prev = last remaining card, next = null.
    const destCards = tasks
      .filter((t) => t.columnId === destColumnId && t.id !== taskId)
      .slice()
      .sort((a, b) => (a.rank < b.rank ? -1 : a.rank > b.rank ? 1 : 0));
    const prevTaskId: string | null = destCards.length > 0 ? destCards[destCards.length - 1].id : null;
    const nextTaskId: string | null = null;
    // optimistic LexoRank: move locally first
    setTasks((prev) => prev.map((t) => (t.id === taskId ? { ...t, columnId: destColumnId } : t)));
    try {
      const updated = await tasksApi.move(taskId, destColumnId, task.version, prevTaskId, nextTaskId);
      setTasks((prev) => prev.map((t) => (t.id === taskId ? updated : t)));
    } catch (err) {
      setConflict(err instanceof Error ? err.message : 'Move rejected (WIP limit or conflict). Reloading…');
      void reload();
    }
  };

  if (!board) return <div className="p-6 text-sm text-slate-500">Loading board…</div>;

  return (
    <div className="p-4">
      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h2 className="text-lg font-semibold">{board.name}</h2>
          <span className={`rounded-full px-2 py-0.5 text-xs ${status === 'synced' ? 'bg-green-100 text-green-800' : 'bg-amber-100 text-amber-800'}`}>
            {status === 'synced' ? '● Live' : '● Syncing…'}
          </span>
          <PresenceAvatars viewers={viewers} />
        </div>
      </div>
      {conflict ? (
        <div className="mb-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" role="alert">
          {conflict} <button className="ml-2 underline" onClick={() => setConflict(null)}>Dismiss</button>
        </div>
      ) : null}
      <DndContext onDragEnd={onDragEnd}>
        <div className="flex gap-3 overflow-x-auto pb-4">
          {board.columns.map((col) => {
            const colTasks = tasks
              .filter((t) => t.columnId === col.id)
              .slice()
              .sort((a, b) => (a.rank < b.rank ? -1 : a.rank > b.rank ? 1 : 0));
            const overLimit = col.wipLimit != null && colTasks.length > col.wipLimit;
            return (
              <Column key={col.id} id={col.id} title={col.name} wipLimit={col.wipLimit} count={colTasks.length} overLimit={overLimit}>
                {colTasks.map((t) => (
                  <DraggableCard key={t.id} task={t} onOpen={setSelected} />
                ))}
              </Column>
            );
          })}
        </div>
      </DndContext>
      {selected ? <TaskModal task={selected} onClose={() => { setSelected(null); void reload(); }} onConflict={setConflict} /> : null}
    </div>
  );
}
