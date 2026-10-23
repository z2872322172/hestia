package com.housekeeping.platform.infrastructure.storage.minio;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code file_object} 数据访问。
 */
@Mapper
public interface FileObjectMapper extends BaseMapper<FileObject> {
}
