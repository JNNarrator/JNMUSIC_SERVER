CREATE TABLE IF NOT EXISTS track (
    track_id     VARCHAR(32)   PRIMARY KEY,
    name         VARCHAR(256)  NOT NULL,
    artist       VARCHAR(256)  NOT NULL,
    album        VARCHAR(256)  DEFAULT NULL,
    cover_url    VARCHAR(512)  DEFAULT NULL,
    duration     INT           NOT NULL,
    format       VARCHAR(16)   DEFAULT NULL,
    file_size    BIGINT        DEFAULT NULL,
    track_number INT           DEFAULT NULL,
    has_lyric    BOOLEAN       DEFAULT FALSE,
    lyric_url    VARCHAR(512)  DEFAULT NULL,
    created_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_track_name ON track(name);
CREATE INDEX IF NOT EXISTS idx_track_artist ON track(artist);
CREATE INDEX IF NOT EXISTS idx_track_album ON track(album);

CREATE TABLE IF NOT EXISTS user_favorite (
    id          BIGINT        PRIMARY KEY AUTO_INCREMENT,
    device_id   VARCHAR(128)  NOT NULL,
    track_id    VARCHAR(32)   NOT NULL,
    created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (device_id, track_id)
);

CREATE INDEX IF NOT EXISTS idx_favorite_device_created ON user_favorite(device_id, created_at);
CREATE INDEX IF NOT EXISTS idx_favorite_track ON user_favorite(track_id);

CREATE TABLE IF NOT EXISTS play_history (
    id          BIGINT        PRIMARY KEY AUTO_INCREMENT,
    device_id   VARCHAR(128)  NOT NULL,
    track_id    VARCHAR(32)   NOT NULL,
    played_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (device_id, track_id)
);

CREATE INDEX IF NOT EXISTS idx_history_device_played ON play_history(device_id, played_at);
CREATE INDEX IF NOT EXISTS idx_history_track ON play_history(track_id);

CREATE TABLE IF NOT EXISTS search_history (
    id          BIGINT        PRIMARY KEY AUTO_INCREMENT,
    device_id   VARCHAR(128)  NOT NULL,
    keyword     VARCHAR(256)  NOT NULL,
    searched_at TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (device_id, keyword)
);

CREATE INDEX IF NOT EXISTS idx_search_device_time ON search_history(device_id, searched_at);

CREATE TABLE IF NOT EXISTS play_queue (
    id          BIGINT        PRIMARY KEY AUTO_INCREMENT,
    device_id   VARCHAR(128)  NOT NULL,
    track_id    VARCHAR(32)   NOT NULL,
    position    INT           NOT NULL,
    created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (device_id, track_id),
    UNIQUE (device_id, position)
);

CREATE INDEX IF NOT EXISTS idx_queue_device_position ON play_queue(device_id, position);
