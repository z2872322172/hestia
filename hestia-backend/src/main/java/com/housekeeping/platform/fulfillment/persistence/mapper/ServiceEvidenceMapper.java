package com.housekeeping.platform.fulfillment.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.fulfillment.persistence.entity.ServiceEvidenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 服务证据 Mapper。
 *
 * <p>唯一键 {@code file_id} 保证重复上传可幂等识别（docs/modules/09 第 16 节）。</p>
 */
@Mapper
public interface ServiceEvidenceMapper extends BaseMapper<ServiceEvidenceEntity> {

    /**
     * 记录下全部证据（时间升序）。
     *
     * @param serviceRecordId 履约记录 ID
     * @return 证据列表
     */
    @Select("SELECT * FROM service_evidence "
            + "WHERE service_record_id = #{serviceRecordId} ORDER BY id ASC")
    List<ServiceEvidenceEntity> selectByRecordId(@Param("serviceRecordId") Long serviceRecordId);

    /**
     * 按文件 ID 查询（重复上传幂等判断）。
     *
     * @param fileId 文件 ID
     * @return 证据或 null
     */
    @Select("SELECT * FROM service_evidence WHERE file_id = #{fileId}")
    ServiceEvidenceEntity selectByFileId(@Param("fileId") Long fileId);
}
