CREATE TABLE click_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    url_id BIGINT NOT NULL,
    clicked_at TIMESTAMP NOT NULL,
    referrer VARCHAR(512) NULL,
    user_agent VARCHAR(512) NULL,
    CONSTRAINT fk_click_events_url FOREIGN KEY (url_id) REFERENCES urls (id)
);

CREATE INDEX ix_click_events_url_id ON click_events (url_id);
CREATE INDEX ix_click_events_clicked_at ON click_events (clicked_at);
