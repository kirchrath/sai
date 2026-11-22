-- Aufgaben, die Agenten als strukturiertes Ergebnis (JSON) anlegen
CREATE TABLE task (
    id               UUID PRIMARY KEY,
    version          BIGINT       NOT NULL,
    user_id          VARCHAR(255) NOT NULL,
    agent_id         VARCHAR(64)  NOT NULL,
    conversation_id  UUID         REFERENCES conversation (id) ON DELETE SET NULL,
    title            VARCHAR(200) NOT NULL,
    description      TEXT         NOT NULL,
    status           VARCHAR(16)  NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'DONE')),
    priority         VARCHAR(16)  NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    due_date         DATE,
    created_at       TIMESTAMPTZ  NOT NULL,
    modified_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX task_user_created_idx ON task (user_id, created_at DESC);
CREATE INDEX task_conversation_idx ON task (conversation_id);

-- Checkliste einer Aufgabe
CREATE TABLE task_step (
    id           UUID PRIMARY KEY,
    version      BIGINT       NOT NULL,
    task_id      UUID         NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    position     INTEGER      NOT NULL,
    text         VARCHAR(500) NOT NULL,
    done         BOOLEAN      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    modified_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX task_step_task_idx ON task_step (task_id, position);
