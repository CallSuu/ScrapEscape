CREATE TABLE api_request (
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    game_id CHAR(36) NULL,
    response_json LONGTEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (game_id) REFERENCES game(game_id) ON DELETE CASCADE
) ENGINE=InnoDB;
