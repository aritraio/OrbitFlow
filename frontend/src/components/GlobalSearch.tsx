import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { searchApi, type SearchHit } from '../api/models';

export function GlobalSearch({ projectId }: { projectId?: string }) {
  const [q, setQ] = useState('');
  const [hits, setHits] = useState<SearchHit[]>([]);
  const [open, setOpen] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (timer.current) clearTimeout(timer.current);
    if (!q.trim()) {
      setHits([]);
      return;
    }
    timer.current = setTimeout(() => {
      searchApi
        .query(q.trim(), projectId)
        .then((r) => {
          setHits(r);
          setOpen(true);
        })
        .catch(() => setHits([]));
    }, 300);
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, [q, projectId]);

  return (
    <div className="relative" data-testid="global-search">
      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        onFocus={() => hits.length > 0 && setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        placeholder="Search tasks & docs…"
        className="w-64 rounded border px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
      />
      {open && hits.length > 0 ? (
        <div className="absolute z-40 mt-1 max-h-80 w-96 overflow-y-auto rounded-lg border bg-white shadow-lg dark:border-slate-700 dark:bg-slate-900">
          {hits.map((h) => (
            <Link
              key={`${h.resourceType}-${h.resourceId}`}
              to={`/projects/${h.projectId}`}
              className="block border-b px-3 py-2 last:border-0 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-800"
            >
              <span className="mr-2 rounded bg-indigo-50 px-1.5 py-0.5 text-[10px] font-bold text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300">
                {h.resourceType}
              </span>
              <span className="text-sm font-medium">{h.title}</span>
              {h.snippet ? <div className="truncate text-xs text-slate-500">{h.snippet}</div> : null}
            </Link>
          ))}
        </div>
      ) : null}
    </div>
  );
}
