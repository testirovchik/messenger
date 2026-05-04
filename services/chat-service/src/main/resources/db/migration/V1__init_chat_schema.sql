-- 1. Create the 'chats' table
CREATE TABLE chats (
                       id BIGSERIAL PRIMARY KEY,
                       chat_title VARCHAR(255),
                       type VARCHAR(50),
                       created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_members (
                              id BIGSERIAL PRIMARY KEY,
                              chat_id BIGINT NOT NULL,
                              user_id BIGINT NOT NULL,
                              role VARCHAR(50),
                              joined_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT fk_chat_member_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
);

CREATE INDEX idx_chat_members_users ON chat_members(user_id);
CREATE UNIQUE INDEX idx_chat_members_chat_user ON chat_members(chat_id, user_id);


CREATE TABLE messages (
                          id BIGSERIAL PRIMARY KEY,
                          chat_id BIGINT NOT NULL,
                          sender_id BIGINT NOT NULL,
                          is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                          is_edited BOOLEAN NOT NULL DEFAULT FALSE,
                          type VARCHAR(50) NOT NULL DEFAULT 'TEXT',
                          content TEXT,
                          created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT fk_message_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
);

CREATE INDEX idx_messages_chat_time ON messages(chat_id, created_at DESC);