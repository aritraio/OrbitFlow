import { useEffect, useState } from 'react';
import { documentsApi, type DocItem, type DocRevision } from '../api/models';
import { MarkdownEditor } from './MarkdownEditor';

export function DocumentWiki({ projectId }: { projectId: string }) {
  const [docs, setDocs] = useState<DocItem[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [revisions, setRevisions] = useState<DocRevision[]>([]);
  const [status, setStatus] = useState<string | null>(null);

  const reload = async () => {
    const list = await documentsApi.list(projectId).catch(() => [] as DocItem[]);
    setDocs(list);
    if (!selectedId && list.length > 0) setSelectedId(list[0].id);
  };

  useEffect(() => {
    void reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  useEffect(() => {
    const doc = docs.find((d) => d.id === selectedId);
    if (doc) {
      setTitle(doc.title);
      setBody(doc.body ?? '');
      documentsApi.revisions(doc.id).then(setRevisions).catch(() => setRevisions([]));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  const createDoc = async () => {
    const created = await documentsApi.create(projectId, 'Untitled document', '');
    setDocs((prev) => [...prev, created]);
    setSelectedId(created.id);
  };

  const save = async (autosave: boolean) => {
    if (!selectedId) return;
    try {
      const updated = await documentsApi.update(selectedId, title, body, autosave);
      setDocs((prev) => prev.map((d) => (d.id === selectedId ? updated : d)));
      if (!autosave) {
        const revs = await documentsApi.revisions(selectedId).catch(() => [] as DocRevision[]);
        setRevisions(revs);
      }
      setStatus(autosave ? 'Draft saved' : 'Revision published');
    } catch (e) {
      setStatus(e instanceof Error ? e.message : 'Save failed');
    }
  };

  const restore = async (n: number) => {
    if (!selectedId) return;
    const updated = await documentsApi.restore(selectedId, n);
    setDocs((prev) => prev.map((d) => (d.id === selectedId ? updated : d)));
    setTitle(updated.title);
    setBody(updated.body ?? '');
    const revs = await documentsApi.revisions(selectedId).catch(() => [] as DocRevision[]);
    setRevisions(revs);
  };

  const roots = docs.filter((d) => !d.parentId);
  const childrenOf = (id: string) => docs.filter((d) => d.parentId === id);

  return (
    <div className="flex gap-4 p-4" data-testid="document-wiki">
      <aside className="w-64 shrink-0 rounded-xl bg-slate-100 p-2 dark:bg-slate-900">
        <button onClick={createDoc} className="mb-2 w-full rounded bg-indigo-600 px-2 py-1.5 text-sm text-white">
          + New Document
        </button>
        {roots.map((d) => (
          <div key={d.id}>
            <button
              onClick={() => setSelectedId(d.id)}
              className={`block w-full rounded px-2 py-1 text-left text-sm ${selectedId === d.id ? 'bg-white font-semibold dark:bg-slate-800' : ''}`}
            >
              {d.title}
            </button>
            {childrenOf(d.id).map((c) => (
              <button
                key={c.id}
                onClick={() => setSelectedId(c.id)}
                className={`block w-full rounded py-1 pl-6 pr-2 text-left text-sm ${selectedId === c.id ? 'bg-white font-semibold dark:bg-slate-800' : ''}`}
              >
                {c.title}
              </button>
            ))}
          </div>
        ))}
        {docs.length === 0 ? <div className="p-2 text-xs text-slate-500">No documents yet.</div> : null}
      </aside>
      <div className="flex-1">
        {selectedId ? (
          <>
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="mb-2 w-full rounded border px-2 py-1.5 text-lg font-semibold dark:border-slate-700 dark:bg-slate-800"
            />
            <MarkdownEditor value={body} onChange={setBody} />
            <div className="mt-2 flex gap-2">
              <button onClick={() => void save(true)} className="rounded border px-3 py-1.5 text-sm dark:border-slate-700">
                Autosave draft
              </button>
              <button onClick={() => void save(false)} className="rounded bg-indigo-600 px-3 py-1.5 text-sm text-white">
                Publish revision
              </button>
              {status ? <span className="self-center text-xs text-slate-500">{status}</span> : null}
            </div>
            <div className="mt-4">
              <h4 className="text-sm font-semibold">Version history</h4>
              <div className="mt-1 space-y-1">
                {revisions.map((r) => (
                  <div key={r.id} className="flex items-center justify-between rounded border px-2 py-1 text-xs dark:border-slate-700">
                    <span>
                      v{r.revisionNumber} — {r.title}
                    </span>
                    <button onClick={() => void restore(r.revisionNumber)} className="underline">
                      Restore
                    </button>
                  </div>
                ))}
              </div>
            </div>
          </>
        ) : (
          <div className="text-sm text-slate-500">Select or create a document.</div>
        )}
      </div>
    </div>
  );
}
