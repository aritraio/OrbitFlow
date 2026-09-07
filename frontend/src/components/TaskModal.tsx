import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Task } from '../api/models';
import { MarkdownEditor } from './MarkdownEditor';

interface Comment {
  id: string;
  authorId: string;
  username: string;
  bodyMarkdown: string;
  bodyHtml: string;
}

export function TaskModal({ task, onClose, onConflict }: { task: Task; onClose: () => void; onConflict: (msg: string) => void }) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [draft, setDraft] = useState('');
  const [description, setDescription] = useState(task.description ?? '');

  useEffect(() => {
    api<Comment[]>(`/api/v1/tasks/${task.id}/comments`).then(setComments).catch(() => setComments([]));
  }, [task.id]);

  const saveDescription = async () => {
    try {
      await api(`/api/v1/tasks/${task.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ description, expectedVersion: task.version }),
      });
      onClose();
    } catch (e) {
      onConflict(e instanceof Error ? e.message : 'Conflict: task changed by a teammate. Reload to merge.');
    }
  };

  const postComment = async () => {
    if (!draft.trim()) return;
    const c = await api<Comment>(`/api/v1/tasks/${task.id}/comments`, {
      method: 'POST',
      body: JSON.stringify({ body: draft }),
    });
    setComments((prev) => [...prev, c]);
    setDraft('');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true">
      <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-xl bg-white p-5 dark:bg-slate-900">
        <div className="flex items-start justify-between">
          <div>
            <div className="text-xs font-mono text-slate-500">{task.taskKey}</div>
            <h2 className="text-lg font-semibold">{task.title}</h2>
          </div>
          <button onClick={onClose} className="rounded px-2 py-1 text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800">
            ✕
          </button>
        </div>

        <div className="mt-4">
          <MarkdownEditor value={description} onChange={setDescription} />
          <button onClick={saveDescription} className="mt-2 rounded bg-indigo-600 px-3 py-1.5 text-sm text-white">
            Save description
          </button>
        </div>

        <div className="mt-6">
          <h3 className="text-sm font-semibold">Comments</h3>
          <div className="mt-2 space-y-2">
            {comments.map((c) => (
              <div key={c.id} className="rounded border border-slate-200 p-2 text-sm dark:border-slate-700">
                <span className="font-medium">@{c.username}</span>
                <div dangerouslySetInnerHTML={{ __html: c.bodyHtml }} />
              </div>
            ))}
          </div>
          <div className="mt-2 flex gap-2">
            <input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="Comment with @mentions…"
              className="flex-1 rounded border border-slate-200 bg-transparent px-2 py-1.5 text-sm dark:border-slate-700"
            />
            <button onClick={postComment} className="rounded bg-slate-900 px-3 py-1.5 text-sm text-white dark:bg-white dark:text-slate-900">
              Post
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
