-- OrbitFlow V1: initial schema (portable: PostgreSQL + H2 in PostgreSQL mode)
-- All UUIDs are generated application-side. Enums stored as VARCHAR. JSON stored as TEXT.

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    username VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(1024),
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    language VARCHAR(16) NOT NULL DEFAULT 'en',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','SUSPENDED','DELETED'))
);

CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    owner_id UUID NOT NULL REFERENCES users(id),
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_workspaces_slug UNIQUE (slug),
    CONSTRAINT chk_workspaces_status CHECK (status IN ('ACTIVE','SUSPENDED','DELETION_PENDING'))
);

CREATE TABLE workspace_memberships (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_workspace_membership UNIQUE (workspace_id, user_id),
    CONSTRAINT chk_wsm_role CHECK (role IN ('OWNER','ADMIN','MEMBER','GUEST')),
    CONSTRAINT chk_wsm_status CHECK (status IN ('INVITED','ACTIVE','DEACTIVATED'))
);

CREATE TABLE invitations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    email VARCHAR(320) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    token_hash VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    invited_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_inv_role CHECK (role IN ('ADMIN','MEMBER','GUEST')),
    CONSTRAINT chk_inv_status CHECK (status IN ('PENDING','ACCEPTED','EXPIRED','REVOKED','CONSUMED'))
);

CREATE TABLE projects (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_key VARCHAR(16) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    icon VARCHAR(16),
    visibility VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    start_date DATE,
    target_date DATE,
    next_task_number BIGINT NOT NULL DEFAULT 1,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_projects_ws_key UNIQUE (workspace_id, project_key),
    CONSTRAINT chk_proj_vis CHECK (visibility IN ('WORKSPACE','PRIVATE')),
    CONSTRAINT chk_proj_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);

CREATE TABLE project_memberships (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_membership UNIQUE (project_id, user_id),
    CONSTRAINT chk_pm_role CHECK (role IN ('MANAGER','MEMBER','GUEST'))
);

CREATE TABLE boards (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    wip_enforcement VARCHAR(16) NOT NULL DEFAULT 'WARNING',
    revision BIGINT NOT NULL DEFAULT 0,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_board_wip CHECK (wip_enforcement IN ('STRICT','WARNING'))
);

CREATE TABLE board_columns (
    id UUID PRIMARY KEY,
    board_id UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    rank_value VARCHAR(64) NOT NULL,
    wip_limit INTEGER,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_col_category CHECK (category IN ('BACKLOG','ACTIVE','REVIEW','DONE'))
);

CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    column_id UUID REFERENCES board_columns(id) ON DELETE SET NULL,
    task_number BIGINT NOT NULL,
    task_key VARCHAR(32) NOT NULL,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    task_type VARCHAR(32) NOT NULL DEFAULT 'TASK',
    priority VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    reporter_id UUID REFERENCES users(id),
    rank_value VARCHAR(64) NOT NULL DEFAULT 'a0',
    start_date DATE,
    due_date TIMESTAMP WITH TIME ZONE,
    estimate_minutes INTEGER,
    spent_minutes INTEGER NOT NULL DEFAULT 0,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    parent_task_id UUID REFERENCES tasks(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tasks_project_number UNIQUE (project_id, task_number),
    CONSTRAINT uq_tasks_project_key UNIQUE (project_id, task_key),
    CONSTRAINT chk_task_type CHECK (task_type IN ('TASK','BUG','STORY','RESEARCH','EPIC')),
    CONSTRAINT chk_task_priority CHECK (priority IN ('LOWEST','LOW','MEDIUM','HIGH','HIGHEST','URGENT'))
);

CREATE TABLE task_assignees (
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id, user_id)
);

CREATE TABLE labels (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(60) NOT NULL,
    color VARCHAR(16) NOT NULL DEFAULT '#6366f1',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_labels_ws_name UNIQUE (workspace_id, name)
);

CREATE TABLE task_labels (
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    label_id UUID NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, label_id)
);

CREATE TABLE checklists (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    position_idx INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE checklist_items (
    id UUID PRIMARY KEY,
    checklist_id UUID NOT NULL REFERENCES checklists(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL,
    done BOOLEAN NOT NULL DEFAULT FALSE,
    assignee_id UUID REFERENCES users(id),
    position_idx INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE task_dependencies (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    depends_on_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    kind VARCHAR(32) NOT NULL DEFAULT 'BLOCKED_BY',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_task_dep UNIQUE (task_id, depends_on_task_id),
    CONSTRAINT chk_dep_kind CHECK (kind IN ('BLOCKED_BY','RELATES_TO'))
);

CREATE TABLE comments (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id),
    body_markdown TEXT NOT NULL,
    body_html TEXT NOT NULL,
    parent_comment_id UUID REFERENCES comments(id),
    edited BOOLEAN NOT NULL DEFAULT FALSE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE comment_revisions (
    id UUID PRIMARY KEY,
    comment_id UUID NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    body_markdown TEXT NOT NULL,
    body_html TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id)
);

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    parent_id UUID REFERENCES documents(id),
    title VARCHAR(300) NOT NULL,
    current_body_markdown TEXT,
    draft_body_markdown TEXT,
    created_by UUID REFERENCES users(id),
    updated_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE document_revisions (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    revision_number BIGINT NOT NULL,
    title VARCHAR(300) NOT NULL,
    body_markdown TEXT,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_doc_rev UNIQUE (document_id, revision_number)
);

CREATE TABLE attachments (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    task_id UUID REFERENCES tasks(id) ON DELETE CASCADE,
    comment_id UUID REFERENCES comments(id) ON DELETE SET NULL,
    document_id UUID REFERENCES documents(id) ON DELETE SET NULL,
    file_name VARCHAR(512) NOT NULL,
    content_type VARCHAR(160) NOT NULL DEFAULT 'application/octet-stream',
    size_bytes BIGINT NOT NULL DEFAULT 0,
    checksum_sha256 VARCHAR(128),
    storage_key VARCHAR(1024) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    uploaded_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_att_status CHECK (status IN ('PENDING','UPLOADED','SCANNING','ACTIVE','QUARANTINED','DELETED'))
);

CREATE TABLE watchers (
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id, user_id)
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    title VARCHAR(300) NOT NULL,
    body TEXT,
    resource_type VARCHAR(64),
    resource_id UUID,
    dedupe_key VARCHAR(256),
    channel VARCHAR(32) NOT NULL DEFAULT 'IN_APP',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    read_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notif_channel CHECK (channel IN ('IN_APP','EMAIL','DIGEST')),
    CONSTRAINT chk_notif_status CHECK (status IN ('PENDING','DELIVERED','READ','FAILED','DEAD_LETTER'))
);

CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    digest_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_notif_pref UNIQUE (user_id, workspace_id, event_type)
);

CREATE TABLE activity_events (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES users(id),
    event_type VARCHAR(80) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    diff_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','PROCESSED','FAILED','DEAD_LETTER'))
);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(256) NOT NULL,
    device_info VARCHAR(512),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_session_token UNIQUE (refresh_token_hash)
);

CREATE TABLE webhook_subscriptions (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    url VARCHAR(2048) NOT NULL,
    secret VARCHAR(256) NOT NULL,
    events VARCHAR(1024) NOT NULL DEFAULT '*',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE milestones (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    due_date DATE,
    completed_at TIMESTAMP WITH TIME ZONE,
    snapshot_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
