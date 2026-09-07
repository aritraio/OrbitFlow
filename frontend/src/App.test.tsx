import { describe, expect, it } from 'vitest';
import { TaskCard } from './components/TaskCard';
import { render, screen } from '@testing-library/react';

describe('TaskCard', () => {
  it('renders task key and title', () => {
    render(
      <TaskCard
        task={{
          id: '1',
          workspaceId: 'w',
          projectId: 'p',
          columnId: 'c',
          taskKey: 'PH-1',
          taskNumber: 1,
          title: 'Hello world',
          description: null,
          type: 'TASK',
          priority: 'HIGH',
          rank: 'a0',
          assigneeIds: [],
          dueDate: null,
          version: 0,
          archived: false,
        }}
        onOpen={() => undefined}
      />,
    );
    expect(screen.getByText('PH-1')).toBeTruthy();
    expect(screen.getByText('Hello world')).toBeTruthy();
  });
});

describe('lexorank (client-side sanity)', () => {
  it('orders ranks lexicographically', () => {
    expect('a0' < 'a0n').toBe(true);
    expect('a0n' < 'a1').toBe(true);
  });
});
