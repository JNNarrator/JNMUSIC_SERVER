package com.jn.music.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文档约定的业务错误码。
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    TRACK_NOT_FOUND(404, "歌曲不存在或已下架"),
    SEARCH_NO_RESULTS(200, "搜索无结果"),
    MEDIA_UNAVAILABLE(502, "播放地址暂时不可用"),
    RATE_LIMITED(429, "请求频率过高"),
    INVALID_PARAMETER(400, "请求参数校验失败"),
    UNAUTHORIZED(401, "未授权"),
    INTERNAL_ERROR(500, "服务内部错误");

    private final Integer httpStatus;
    private final String message;
}
