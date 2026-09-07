import { useState } from 'react';

export function MarkdownEditor({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [preview, setPreview] = useState(false);
  return (
    <div className="rounded-lg border border-slate-200 dark:border-slate-700">
      <div className="flex gap-2 border-b border-slate-200 p-2 text-xs dark:border-slate-700">
        <button type="button" onClick={() => setPreview(false)} className={!preview ? 'font-bold' : ''}>
          Write
        </button>
        <button type="button" onClick={() => setPreview(true)} className={preview ? 'font-bold' : ''}>
          Preview
        </button>
      </div>
      {preview ? (
        <div className="prose prose-sm max-w-none p-3 dark:prose-invert" data-testid="markdown-preview">
          {value || <span className="text-slate-400">Nothing to preview</span>}
        </div>
      ) : (
        <textarea
          value={value}
          onChange={(e) => onChange(e.target.value)}
          rows={5}
          className="w-full bg-transparent p-3 text-sm outline-none"
          placeholder="Describe the task… supports **bold**, `code`, @mentions"
        />
      )}
    </div>
  );
}
