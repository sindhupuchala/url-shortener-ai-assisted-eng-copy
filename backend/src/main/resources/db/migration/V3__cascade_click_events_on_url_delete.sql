-- The expired-link cleanup job (Scenario 3) deletes rows from `urls`; without ON DELETE CASCADE
-- that would fail with a foreign key violation for any expired url that was ever clicked.
ALTER TABLE click_events DROP CONSTRAINT fk_click_events_url;
ALTER TABLE click_events ADD CONSTRAINT fk_click_events_url FOREIGN KEY (url_id) REFERENCES urls (id) ON DELETE CASCADE;
