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
