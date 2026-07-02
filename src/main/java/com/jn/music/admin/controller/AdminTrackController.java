package com.jn.music.admin.controller;

import com.jn.music.admin.service.AdminTokenStore;
import com.jn.music.common.ApiError;
import com.jn.music.common.ApiResponse;
import com.jn.music.common.TraceIdContext;
import com.jn.music.common.exception.BusinessException;
import com.jn.music.common.enums.ErrorCode;
import com.jn.music.admin.dto.AdminTrackRequest;
import com.jn.music.track.domain.Track;
import com.jn.music.track.mapper.TrackMapper;
import com.jn.music.track.service.TrackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/admin/tracks")
public class AdminTrackController {

    private final TrackService trackService;
    private final TrackMapper trackMapper;
    private final AdminTokenStore tokenStore;

    public AdminTrackController(TrackService trackService,
                                TrackMapper trackMapper,
                                AdminTokenStore tokenStore) {
        this.trackService = trackService;
        this.trackMapper = trackMapper;
        this.tokenStore = tokenStore;
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Track>> upload(
            HttpServletRequest request,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "X-Admin-User", required = false) String username,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") @NotBlank(message = "上传类型不能为空") String type,
            @RequestParam(value = "trackId", required = false) String trackId) {
        checkAdminAuth(token, username);
        if (StringUtils.hasText(trackId) && trackService.getTrackById(trackId.trim()) != null) {
            return ResponseEntity.badRequest().body(error(ErrorCode.INVALID_PARAMETER, "歌曲ID已存在"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(error(ErrorCode.INVALID_PARAMETER, "文件不能为空"));
        }
        String resolvedTrackId = StringUtils.hasText(trackId) ? trackId.trim() : ("T" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        if (StringUtils.hasText(trackId) && trackService.getTrackById(resolvedTrackId) != null) {
            return ResponseEntity.badRequest().body(error(ErrorCode.INVALID_PARAMETER, "歌曲ID已存在"));
        }
        String path = resolvePath(type, resolvedTrackId);
        try {
            forwardFile(path, file);
            return ResponseEntity.ok(ApiResponse.success(Track.builder()
                    .trackId(resolvedTrackId)
                    .name(file.getOriginalFilename())
                    .build()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(ErrorCode.MEDIA_UNAVAILABLE, "上传失败: " + ex.getMessage()));
        }
    }

    @GetMapping("/{trackId}")
    public ResponseEntity<ApiResponse<Track>> detail(
            HttpServletRequest request,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "X-Admin-User", required = false) String username,
            @PathVariable("trackId") String trackId) {
        checkAdminAuth(token, username);
        Track track = trackMapper.selectById(trackId);
        if (track == null) {
            throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
        }
        return ResponseEntity.ok(ApiResponse.success(track));
    }

    private void checkAdminAuth(String token, String username) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(username) || !tokenStore.isValid(token, username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "管理后台未登录");
        }
    }

    private String resolvePath(String type, String trackId) {
        if ("audio".equalsIgnoreCase(type)) {
            return "/audio/" + trackId;
        }
        if ("cover".equalsIgnoreCase(type)) {
            return "/covers/" + trackId + ".jpg";
        }
        if ("lyric".equalsIgnoreCase(type)) {
            return "/lyrics/" + trackId + ".lrc";
        }
        throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不支持的上传类型: " + type);
    }

    private <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ApiError.builder().code(errorCode.name()).message(message).build())
                .traceId(TraceIdContext.getTraceId())
                .build();
    }

    private void forwardFile(String path, MultipartFile file) throws IOException {
        String boundary = "----FormBoundary" + System.currentTimeMillis();
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)) {
            fileName = "upload.bin";
        }
        HttpURLConnection connection = (HttpURLConnection) new URL("http://jn_file.88933.vip:27472" + path).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA + "; boundary=" + boundary);
        try (InputStream inputStream = file.getInputStream();
             java.io.OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(("--" + boundary + "\r\n").getBytes());
            outputStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n").getBytes());
            outputStream.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes());
            StreamUtils.copy(inputStream, outputStream);
            outputStream.write(("\r\n--" + boundary + "--\r\n").getBytes());
            outputStream.flush();
        }
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            String message = readFully(connection.getErrorStream());
            throw new IOException("dufs 返回状态码 " + status + ": " + message);
        }
        readFully(connection.getInputStream());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Track>> save(
            HttpServletRequest request,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "X-Admin-User", required = false) String username,
            @Valid @RequestBody AdminTrackRequest trackRequest) {
        checkAdminAuth(token, username);
        Track track = Track.builder()
                .trackId(trackRequest.getTrackId())
                .name(trackRequest.getName())
                .artist(trackRequest.getArtist())
                .album(trackRequest.getAlbum())
                .duration(trackRequest.getDuration())
                .format(trackRequest.getFormat())
                .fileSize(trackRequest.getFileSize())
                .trackNumber(trackRequest.getTrackNumber())
                .hasLyric(Boolean.TRUE.equals(trackRequest.getHasLyric()))
                .coverUrl(trackRequest.getCoverUrl())
                .lyricUrl(trackRequest.getLyricUrl())
                .build();
        Track existing = trackMapper.selectById(track.getTrackId());
        if (existing == null) {
            int inserted = trackMapper.insert(track);
            if (inserted < 1) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "保存失败");
            }
        } else {
            Track updateTrack = Track.builder()
                    .trackId(track.getTrackId())
                    .name(track.getName())
                    .artist(track.getArtist())
                    .album(track.getAlbum())
                    .duration(track.getDuration())
                    .format(track.getFormat())
                    .fileSize(track.getFileSize())
                    .trackNumber(track.getTrackNumber())
                    .hasLyric(track.getHasLyric())
                    .coverUrl(track.getCoverUrl())
                    .lyricUrl(track.getLyricUrl())
                    .build();
            int updated = trackMapper.updateById(updateTrack);
            if (updated < 1) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "保存失败");
            }
            track = updateTrack;
        }
        return ResponseEntity.ok(ApiResponse.success(track));
    }

    private String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        return StreamUtils.copyToString(stream, java.nio.charset.Charset.forName("UTF-8"));
    }
}
