CREATE TABLE IF NOT EXISTS track (
    track_id     VARCHAR(32)   PRIMARY KEY COMMENT '全局唯一歌曲ID，如 T0000421',
    name         VARCHAR(256)  NOT NULL COMMENT '歌曲名',
    artist       VARCHAR(256)  NOT NULL COMMENT '歌手名',
    album        VARCHAR(256)  DEFAULT NULL COMMENT '专辑名',
    cover_url    VARCHAR(512)  DEFAULT NULL COMMENT '封面图片相对路径',
    duration     INT           NOT NULL COMMENT '时长（秒）',
    format       VARCHAR(16)   DEFAULT NULL COMMENT '文件格式：flac/mp3/wav/aac',
    file_size    BIGINT        DEFAULT NULL COMMENT '文件大小（字节）',
    track_number INT           DEFAULT NULL COMMENT '专辑内曲目序号',
    has_lyric    TINYINT(1)    DEFAULT 0 COMMENT '是否有歌词文件',
    lyric_url    VARCHAR(512)  DEFAULT NULL COMMENT '歌词文件相对路径',
    created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_name (name),
    INDEX idx_artist (artist),
    INDEX idx_album (album)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌曲元数据表';

CREATE TABLE IF NOT EXISTS user_favorite (
    id          BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '收藏记录ID',
    device_id   VARCHAR(128)  NOT NULL COMMENT '匿名设备ID',
    track_id    VARCHAR(32)   NOT NULL COMMENT '歌曲ID',
    created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_favorite_device_track (device_id, track_id),
    INDEX idx_favorite_device_created (device_id, created_at),
    INDEX idx_favorite_track (track_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏表';

CREATE TABLE IF NOT EXISTS play_history (
    id          BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '播放历史ID',
    device_id   VARCHAR(128)  NOT NULL COMMENT '匿名设备ID',
    track_id    VARCHAR(32)   NOT NULL COMMENT '歌曲ID',
    played_at   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '最近播放时间',
    UNIQUE KEY uk_history_device_track (device_id, track_id),
    INDEX idx_history_device_played (device_id, played_at),
    INDEX idx_history_track (track_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='播放历史表';

CREATE TABLE IF NOT EXISTS search_history (
    id          BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '搜索历史ID',
    device_id   VARCHAR(128)  NOT NULL COMMENT '匿名设备ID',
    keyword     VARCHAR(256)  NOT NULL COMMENT '搜索关键词',
    searched_at DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '最近搜索时间',
    UNIQUE KEY uk_search_device_keyword (device_id, keyword),
    INDEX idx_search_device_time (device_id, searched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表';

CREATE TABLE IF NOT EXISTS play_queue (
    id          BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '播放队列ID',
    device_id   VARCHAR(128)  NOT NULL COMMENT '匿名设备ID',
    track_id    VARCHAR(32)   NOT NULL COMMENT '歌曲ID',
    position    INT           NOT NULL COMMENT '队列位置，从0开始',
    created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_queue_device_track (device_id, track_id),
    UNIQUE KEY uk_queue_device_position (device_id, position),
    INDEX idx_queue_device_position (device_id, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='播放队列表';
