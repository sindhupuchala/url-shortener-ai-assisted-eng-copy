CREATE TABLE urls (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- nullable at the DB level: the code is derived from this row's own auto-increment id
    -- (see UrlService), so it is assigned in a follow-up UPDATE within the same transaction,
    -- after the INSERT has produced the id. It is never null once the transaction commits.
    code VARCHAR(16) NULL,
    custom_alias VARCHAR(64) NULL,
    original_url VARCHAR(2048) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NULL
);

CREATE UNIQUE INDEX ux_urls_code ON urls (code);
CREATE UNIQUE INDEX ux_urls_custom_alias ON urls (custom_alias);
CREATE INDEX ix_urls_owner_id ON urls (owner_id);
