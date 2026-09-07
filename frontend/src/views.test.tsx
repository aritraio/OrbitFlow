import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { GlobalSearch } from './components/GlobalSearch';
import { DocumentWiki } from './components/DocumentWiki';
import { ReportsView } from './components/ReportsView';
import { WorkspaceMembersModal } from './components/WorkspaceMembersModal';

function jsonResponse(data: unknown) {
  return { ok: true, status: 200, text: async () => JSON.stringify(data) };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('GlobalSearch', () => {
  it('renders a debounced search input', () => {
    render(
      <BrowserRouter>
        <GlobalSearch />
      </BrowserRouter>,
    );
    expect(screen.getByPlaceholderText('Search tasks & docs…')).toBeTruthy();
  });

  it('shows task and document hits after typing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse([{ resourceType: 'Task', resourceId: 't1', title: 'PH-1 Fix', snippet: '…', projectId: 'p1' }])),
    );
    render(
      <BrowserRouter>
        <GlobalSearch />
      </BrowserRouter>,
    );
    fireEvent.change(screen.getByPlaceholderText('Search tasks & docs…'), { target: { value: 'fix' } });
    expect(await screen.findByText('PH-1 Fix')).toBeTruthy();
  });
});

describe('DocumentWiki', () => {
  it('shows empty state and new-document button', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse([])),
    );
    render(<DocumentWiki projectId="p1" />);
    expect(await screen.findByText('No documents yet.')).toBeTruthy();
    expect(screen.getByText('+ New Document')).toBeTruthy();
  });
});

describe('ReportsView', () => {
  it('renders totals and milestones', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.includes('/report')) {
          return jsonResponse({
            projectId: 'p1',
            timezone: 'UTC',
            generatedAt: new Date().toISOString(),
            total: 5,
            completed: 2,
            overdue: 1,
            blocked: 0,
            byStatus: { Backlog: 3, Done: 2 },
            byPriority: { HIGH: 1 },
            avgCycleHours: 4.5,
          });
        }
        return jsonResponse([]);
      }),
    );
    render(<ReportsView projectId="p1" />);
    expect(await screen.findByText('Milestones')).toBeTruthy();
    expect(screen.getByText('By status')).toBeTruthy();
  });
});

describe('WorkspaceMembersModal', () => {
  it('lists members with roles and invite form', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse([{ userId: 'u1', email: 'a@example.com', username: 'alice', role: 'OWNER', status: 'ACTIVE' }]),
      ),
    );
    render(<WorkspaceMembersModal workspaceId="w1" onClose={() => undefined} />);
    expect(await screen.findByText('@alice')).toBeTruthy();
    expect(screen.getByPlaceholderText('teammate@example.com')).toBeTruthy();
  });
});
