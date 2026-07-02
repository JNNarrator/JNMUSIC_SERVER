package com.jn.music.user.service;

import com.jn.music.user.dto.QueueItemDTO;
import com.jn.music.user.dto.QueueItemRequest;
import java.util.List;

/**
 * 播放队列业务接口。
 */
public interface QueueService {

    List<QueueItemDTO> listQueue();

    void saveQueue(List<QueueItemRequest> items);

    void appendTrack(String trackId);

    void removeTrack(String trackId);
}
