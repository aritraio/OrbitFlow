-- OrbitFlow V3: composite indexes for bounded search + due-date reminders

CREATE INDEX idx_tasks_ws_archived ON tasks(workspace_id, archived);
CREATE INDEX idx_tasks_ws_project ON tasks(workspace_id, project_id);
CREATE INDEX idx_tasks_due_archived ON tasks(due_date, archived);
CREATE INDEX idx_docs_ws ON documents(workspace_id);
CREATE INDEX idx_docs_ws_project ON documents(workspace_id, project_id);
