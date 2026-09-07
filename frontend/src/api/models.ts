import { api } from './client';

export interface Workspace {
  id: string;
  name: string;
  slug: string;
  ownerId: string;
  role: string;
  status: string;
}

export interface Project {
  id: string;
  workspaceId: string;
  key: string;
  name: string;
  visibility: string;
  status: string;
  boardId: string | null;
}

export interface BoardColumn {
  id: string;
  boardId: string;
  name: string;
  category: string;
  rank: string;
  wipLimit: number | null;
  version: number;
  taskCount: number;
}

export interface Board {
  id: string;
  projectId: string;
  name: string;
  revision: number;
  columns: BoardColumn[];
}

export interface Task {
  id: string;
  workspaceId: string;
  projectId: string;
  columnId: string | null;
  taskKey: string;
  taskNumber: number;
  title: string;
  description: string | null;
  type: string;
  priority: string;
  rank: string;
  assigneeIds: string[];
  dueDate: string | null;
  version: number;
  archived: boolean;
}

export const workspacesApi = {
  list: () => api<Workspace[]>('/api/v1/workspaces'),
  create: (name: string) => api<Workspace>('/api/v1/workspaces', { method: 'POST', body: JSON.stringify({ name }) }),
};

export const projectsApi = {
  list: (workspaceId: string) => api<Project[]>(`/api/v1/workspaces/${workspaceId}/projects`),
  create: (workspaceId: string, key: string, name: string) =>
    api<Project>(`/api/v1/workspaces/${workspaceId}/projects`, {
      method: 'POST',
      body: JSON.stringify({ key, name }),
    }),
};

export const boardsApi = {
  byProject: (projectId: string) => api<Board>(`/api/v1/projects/${projectId}/board`),
};

export const tasksApi = {
  list: (projectId: string) => api<Task[]>(`/api/v1/projects/${projectId}/tasks`),
  create: (projectId: string, title: string, columnId?: string) =>
    api<Task>(`/api/v1/projects/${projectId}/tasks`, {
      method: 'POST',
      body: JSON.stringify({ title, columnId }),
    }),
  move: (taskId: string, destinationColumnId: string, expectedVersion: number, prevTaskId?: string | null, nextTaskId?: string | null) =>
    api<Task>(`/api/v1/tasks/${taskId}/move`, {
      method: 'POST',
      body: JSON.stringify({ destinationColumnId, expectedVersion, prevTaskId, nextTaskId }),
    }),
  update: (taskId: string, patch: Record<string, unknown>, expectedVersion: number) =>
    api<Task>(`/api/v1/tasks/${taskId}`, {
      method: 'PATCH',
      body: JSON.stringify({ ...patch, expectedVersion }),
    }),
};
