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

export interface DocItem {
  id: string;
  projectId: string;
  parentId: string | null;
  title: string;
  body: string | null;
  draft: string | null;
  version: number;
  updatedAt: string | null;
}

export interface DocRevision {
  id: string;
  revisionNumber: number;
  title: string;
  body: string | null;
  createdAt: string;
}

export interface SearchHit {
  resourceType: string;
  resourceId: string;
  title: string;
  snippet: string;
  projectId: string;
}

export interface Member {
  userId: string;
  email: string;
  username: string;
  role: string;
  status: string;
}

export interface ProjectReport {
  projectId: string;
  timezone: string;
  generatedAt: string;
  total: number;
  completed: number;
  overdue: number;
  blocked: number;
  byStatus: Record<string, number>;
  byPriority: Record<string, number>;
  avgCycleHours: number;
}

export interface Milestone {
  id: string;
  projectId: string;
  name: string;
  dueDate: string | null;
  completedAt: string | null;
  snapshot: string | null;
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

export const documentsApi = {
  list: (projectId: string) => api<DocItem[]>(`/api/v1/projects/${projectId}/documents`),
  create: (projectId: string, title: string, body?: string, parentId?: string | null) =>
    api<DocItem>(`/api/v1/projects/${projectId}/documents`, {
      method: 'POST',
      body: JSON.stringify({ title, body, parentId }),
    }),
  update: (documentId: string, title: string, body: string, autosave = false) =>
    api<DocItem>(`/api/v1/documents/${documentId}`, {
      method: 'PATCH',
      body: JSON.stringify({ title, body, autosave }),
    }),
  revisions: (documentId: string) => api<DocRevision[]>(`/api/v1/documents/${documentId}/revisions`),
  restore: (documentId: string, revisionNumber: number) =>
    api<DocItem>(`/api/v1/documents/${documentId}/restore/${revisionNumber}`, { method: 'POST' }),
};

export const searchApi = {
  query: (q: string, projectId?: string) =>
    api<SearchHit[]>(`/api/v1/search?q=${encodeURIComponent(q)}${projectId ? `&projectId=${projectId}` : ''}`),
};

export const reportsApi = {
  report: (projectId: string, timezone = 'UTC') =>
    api<ProjectReport>(`/api/v1/projects/${projectId}/report?timezone=${encodeURIComponent(timezone)}`),
  milestones: (projectId: string) => api<Milestone[]>(`/api/v1/projects/${projectId}/milestones`),
  createMilestone: (projectId: string, name: string, dueDate?: string) =>
    api<Milestone>(`/api/v1/projects/${projectId}/milestones`, {
      method: 'POST',
      body: JSON.stringify({ name, dueDate }),
    }),
  completeMilestone: (milestoneId: string) =>
    api<Milestone>(`/api/v1/milestones/${milestoneId}/complete`, { method: 'POST' }),
};

export const membersApi = {
  list: (workspaceId: string) => api<Member[]>(`/api/v1/workspaces/${workspaceId}/members`),
  invite: (workspaceId: string, email: string, role = 'MEMBER') =>
    api<{ token: string; invitationId: string }>(`/api/v1/workspaces/${workspaceId}/invitations`, {
      method: 'POST',
      body: JSON.stringify({ email, role }),
    }),
};
