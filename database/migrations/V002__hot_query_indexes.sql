-- Hot query indexes for Part 1 snapshot/search/sync paths.
CREATE INDEX idx_project_home_snapshot ON project(account_id, last_active_at DESC) INCLUDE(title, status, priority) WHERE deleted_at IS NULL;
CREATE INDEX idx_goal_workspace_snapshot ON goal(project_id, status, priority DESC, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_source_library_recent ON source(account_id, updated_at DESC) INCLUDE(title, state, mime_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_note_project_search ON note(project_id, updated_at DESC) WHERE deleted_at IS NULL AND archived = false;
CREATE INDEX idx_message_chat_page ON conversation_message(conversation_id, created_at DESC, id DESC);
CREATE INDEX idx_flashcard_deck_cards ON flashcard(deck_id, created_at, id) WHERE deleted_at IS NULL;
CREATE INDEX idx_memory_retrieval ON memory_item(account_id, status, category, updated_at DESC) WHERE status='ACTIVE';
CREATE INDEX idx_notification_pending ON notification_intent(account_id, status, scheduled_for) WHERE status='PENDING';
