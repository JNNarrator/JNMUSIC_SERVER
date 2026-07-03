package com.jn.music.track.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 播放地址支持的音质枚举。
 */
@Getter
@RequiredArgsConstructor
public enum MediaQuality {

    FLAC("flac"),
    MP3_320("mp3_320"),
	MP3("mp3"),
    MP3_128("mp3_128");

    private final String code;
}
