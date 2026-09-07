export function PresenceAvatars({ viewers }: { viewers: string[] }) {
  if (viewers.length === 0) return null;
  return (
    <div className="flex items-center gap-2" title={`${viewers.length} viewing`}>
      <div className="flex -space-x-2">
        {viewers.slice(0, 5).map((v) => (
          <span
            key={v}
            className="flex h-7 w-7 items-center justify-center rounded-full bg-indigo-500 text-[11px] font-bold text-white ring-2 ring-white dark:ring-slate-900"
          >
            {v.slice(0, 2).toUpperCase()}
          </span>
        ))}
      </div>
      <span className="text-xs text-slate-500">
        {viewers.length === 1 ? '1 person viewing' : `${viewers.length} viewing this board`}
      </span>
    </div>
  );
}
