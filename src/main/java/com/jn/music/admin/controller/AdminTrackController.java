package com.jn.music.admin.controller;

import com.jn.music.admin.service.AdminTokenStore;
import com.jn.music.common.ApiError;
import com.jn.music.common.ApiResponse;
import com.jn.music.common.TraceIdContext;
import com.jn.music.common.exception.BusinessException;
import com.jn.music.common.enums.ErrorCode;
import com.jn.music.admin.dto.AdminTrackRequest;
import com.jn.music.admin.dto.AdminUploadResponse;
import com.jn.music.track.domain.Track;
import com.jn.music.track.mapper.TrackMapper;
import com.jn.music.track.service.TrackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@RestController
@RequestMapping("/api/v1/admin/tracks")
public class AdminTrackController {

    private static final String FILE_SERVER_BASE_URL = "http://jn_file.88933.vip:27472";
    private static final String UNKNOWN_ARTIST = "\u672a\u77e5";
    private static final String UNKNOWN_ALBUM = "\u672a\u77e5";
    private static final String UNKNOWN_FORMAT = "\u672a\u77e5";

    private final TrackService trackService;
    private final TrackMapper trackMapper;
    private final AdminTokenStore tokenStore;
    private final FileForwarder fileForwarder;
    private final FileDeleter fileDeleter;

    @Autowired
    public AdminTrackController(TrackService trackService,
                                TrackMapper trackMapper,
                                AdminTokenStore tokenStore) {
        this(trackService, trackMapper, tokenStore, null, null);
    }

    AdminTrackController(TrackService trackService,
                         TrackMapper trackMapper,
                         AdminTokenStore tokenStore,
                         FileForwarder fileForwarder) {
        this(trackService, trackMapper, tokenStore, fileForwarder, null);
    }

    AdminTrackController(TrackService trackService,
                         TrackMapper trackMapper,
                         AdminTokenStore tokenStore,
                         FileForwarder fileForwarder,
                         FileDeleter fileDeleter) {
        this.trackService = trackService;
        this.trackMapper = trackMapper;
        this.tokenStore = tokenStore;
        this.fileForwarder = fileForwarder != null ? fileForwarder : this::forwardFile;
        this.fileDeleter = fileDeleter != null ? fileDeleter : this::deleteFile;
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<AdminUploadResponse>> upload(
            HttpServletRequest request,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "X-Admin-User", required = false) String username,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") @NotBlank(message = "上传类型不能为空") String type,
            @RequestParam(value = "trackId", required = false) String trackId) {
        checkAdminAuth(token, username);
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(error(ErrorCode.INVALID_PARAMETER, "文件不能为空"));
        }
        String resolvedTrackId = StringUtils.hasText(trackId) ? trackId.trim() : generateTrackId();
        String originalFilename = file.getOriginalFilename();
        String format = resolveFormat(type, originalFilename, file.getContentType());
        String path = resolvePath(type, resolvedTrackId, format);
        try {
            fileForwarder.forward(path, file);
            return ResponseEntity.ok(ApiResponse.success(AdminUploadResponse.builder()
                    .trackId(resolvedTrackId)
                    .type(type)
                    .fileName(originalFilename)
                    .format(format)
                    .fileSize(file.getSize())
                    .url(FILE_SERVER_BASE_URL + path)
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

    @DeleteMapping("/{trackId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            HttpServletRequest request,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "X-Admin-User", required = false) String username,
            @PathVariable("trackId") String trackId) {
        checkAdminAuth(token, username);
        Track track = trackMapper.selectById(trackId);
        if (track == null) {
            throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
        }
        int deleted = trackMapper.deleteById(trackId);
        if (deleted < 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "删除失败");
        }
        deleteTrackFiles(track);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private void checkAdminAuth(String token, String username) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(username) || !tokenStore.isValid(token, username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "管理后台未登录");
        }
    }

    private String resolvePath(String type, String trackId, String format) {
        if ("audio".equalsIgnoreCase(type)) {
            return "/audio/" + trackId + "." + format;
        }
        if ("cover".equalsIgnoreCase(type)) {
            return "/covers/" + trackId + ".jpg";
        }
        if ("lyric".equalsIgnoreCase(type)) {
            return "/lyrics/" + trackId + ".lrc";
        }
        throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不支持的上传类型: " + type);
    }

    private String resolveFormat(String type, String filename, String contentType) {
        String extension = StringUtils.getFilenameExtension(filename);
        if (StringUtils.hasText(extension)) {
            return extension.trim().toLowerCase(Locale.ROOT);
        }
        if ("audio".equalsIgnoreCase(type) && StringUtils.hasText(contentType)) {
            String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
            if ("audio/mpeg".equals(normalizedContentType) || "audio/mp3".equals(normalizedContentType)) {
                return "mp3";
            }
            if ("audio/flac".equals(normalizedContentType) || "audio/x-flac".equals(normalizedContentType)) {
                return "flac";
            }
            if ("audio/wav".equals(normalizedContentType)
                    || "audio/wave".equals(normalizedContentType)
                    || "audio/x-wav".equals(normalizedContentType)) {
                return "wav";
            }
            if ("audio/aac".equals(normalizedContentType) || "audio/x-aac".equals(normalizedContentType)) {
                return "aac";
            }
            if ("audio/ogg".equals(normalizedContentType)) {
                return "ogg";
            }
            return "bin";
        }
        if ("cover".equalsIgnoreCase(type)) {
            return "jpg";
        }
        if ("lyric".equalsIgnoreCase(type)) {
            return "lrc";
        }
        return "bin";
    }

    private String generateTrackId() {
        String trackId;
        do {
            trackId = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (trackService.getTrackById(trackId) != null);
        return trackId;
    }

    private String normalizeArtist(String artist) {
        return StringUtils.hasText(artist) ? artist.trim() : UNKNOWN_ARTIST;
    }

    private String normalizeAlbum(String album) {
        return StringUtils.hasText(album) ? album.trim() : UNKNOWN_ALBUM;
    }

    private String normalizeFormat(String format) {
        return StringUtils.hasText(format) ? format.trim().toLowerCase(Locale.ROOT) : UNKNOWN_FORMAT;
    }

    private void deleteTrackFiles(Track track) {
        deletePathIfPresent(audioPath(track));
        deletePathIfPresent(relativePath(track.getCoverUrl()));
        deletePathIfPresent(relativePath(track.getLyricUrl()));
    }

    private String audioPath(Track track) {
        if (track == null || !StringUtils.hasText(track.getFormat())) {
            return null;
        }
        return "/audio/" + track.getTrackId() + "." + track.getFormat().trim().toLowerCase(Locale.ROOT);
    }

    private String relativePath(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith(FILE_SERVER_BASE_URL)) {
            trimmed = trimmed.substring(FILE_SERVER_BASE_URL.length());
        }
        return trimmed.startsWith("/") ? trimmed : null;
    }

    private void deletePathIfPresent(String path) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        try {
            fileDeleter.delete(path);
        } catch (Exception ignored) {
            // 数据库记录已删除，单个旧资源清理失败不阻塞其余文件清理。
        }
    }

    private <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ApiError.builder().code(errorCode.name()).message(message).build())
                .traceId(TraceIdContext.getTraceId())
                .build();
    }

    private void forwardFile(String path, MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)) {
            fileName = "upload.bin";
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(FILE_SERVER_BASE_URL + path).openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, contentType);
        try (InputStream inputStream = file.getInputStream();
             java.io.OutputStream outputStream = connection.getOutputStream()) {
            StreamUtils.copy(inputStream, outputStream);
            outputStream.flush();
        }
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            String message = readFully(connection.getErrorStream());
            throw new IOException("dufs 返回状态码 " + status + ": " + message);
        }
        readFully(connection.getInputStream());
    }

    private void deleteFile(String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(FILE_SERVER_BASE_URL + path).openConnection();
        connection.setRequestMethod("DELETE");
        int status = connection.getResponseCode();
        if (status != HttpStatus.NOT_FOUND.value() && (status < 200 || status >= 300)) {
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
        String resolvedTrackId = StringUtils.hasText(trackRequest.getTrackId()) ? trackRequest.getTrackId().trim() : generateTrackId();
        Integer duration = trackRequest.getDuration() != null ? Math.max(trackRequest.getDuration(), 0) : 0;
        Long fileSize = trackRequest.getFileSize() != null ? Math.max(trackRequest.getFileSize(), 0L) : 0L;
        Integer trackNumber = trackRequest.getTrackNumber() != null && trackRequest.getTrackNumber() > 0
                ? trackRequest.getTrackNumber()
                : 1;
        Track track = Track.builder()
                .trackId(resolvedTrackId)
                .name(trackRequest.getName().trim())
                .artist(normalizeArtist(trackRequest.getArtist()))
                .album(normalizeAlbum(trackRequest.getAlbum()))
                // 新增页不再暴露这些字段，保存时统一兜底，避免数据库留下需要人工维护的空值。
                .duration(duration)
                .format(normalizeFormat(trackRequest.getFormat()))
                .fileSize(fileSize)
                .trackNumber(trackNumber)
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
        return StreamUtils.copyToString(stream, StandardCharsets.UTF_8);
    }

    interface FileForwarder {
        void forward(String path, MultipartFile file) throws IOException;
    }

    interface FileDeleter {
        void delete(String path) throws IOException;
    }
}
