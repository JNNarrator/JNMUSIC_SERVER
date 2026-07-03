package com.jn.music.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jn.music.user.domain.PlayHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlayHistoryMapper extends BaseMapper<PlayHistory> {
}
