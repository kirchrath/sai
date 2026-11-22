-- Erweiterungen fuer pgvector
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Konversationen pro Benutzer (Keycloak-Subject)
-- Alle Entities: UUID als ID, Version (optimistisches Locking), Erstellungs- und Aenderungszeitpunkt
CREATE TABLE conversation (
    id           UUID PRIMARY KEY,
    version      BIGINT       NOT NULL,
    user_id      VARCHAR(255) NOT NULL,
    agent_id     VARCHAR(64)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    modified_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX conversation_user_modified_idx ON conversation (user_id, modified_at DESC);

-- Vollstaendiger, anzeigbarer Verlauf (unabhaengig vom Kontextfenster der ChatMemory)
CREATE TABLE chat_message (
    id               UUID PRIMARY KEY,
    version          BIGINT      NOT NULL,
    conversation_id  UUID        NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    role             VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content          TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    modified_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX chat_message_conversation_idx ON chat_message (conversation_id, created_at);

-- Hochgeladene Wissensdokumente pro Agent
CREATE TABLE knowledge_document (
    id            UUID PRIMARY KEY,
    version       BIGINT       NOT NULL,
    agent_id      VARCHAR(64)  NOT NULL,
    filename      VARCHAR(500) NOT NULL,
    content_type  VARCHAR(200),
    size_bytes    BIGINT       NOT NULL,
    chunk_count   INTEGER      NOT NULL,
    uploaded_by   VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    modified_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX knowledge_document_agent_idx ON knowledge_document (agent_id, created_at DESC);

-- Spring AI JDBC ChatMemory (Schema aus spring-ai-model-chat-memory-repository-jdbc, PostgreSQL)
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL,
    content         TEXT        NOT NULL,
    type            VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"     TIMESTAMP   NOT NULL,
    sequence_id     BIGINT      NOT NULL
);
CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
    ON SPRING_AI_CHAT_MEMORY (conversation_id, "timestamp");
CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_SEQUENCE_ID_IDX
    ON SPRING_AI_CHAT_MEMORY (conversation_id, sequence_id);

-- Spring AI PgVectorStore; 384 Dimensionen = all-MiniLM-L6-v2 (Transformers/ONNX)
CREATE TABLE IF NOT EXISTS vector_store (
    id         UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content    TEXT,
    metadata   JSON,
    embedding  VECTOR(384)
);
CREATE INDEX IF NOT EXISTS spring_ai_vector_index
    ON vector_store USING HNSW (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS vector_store_agent_idx
    ON vector_store ((metadata ->> 'agentId'));
