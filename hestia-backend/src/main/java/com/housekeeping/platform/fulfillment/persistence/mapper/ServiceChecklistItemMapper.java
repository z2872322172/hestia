package com.housekeeping.platform.fulfillment.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.fulfillment.persistence.entity.ServiceChecklistItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 服务检查项 Mapper。
 *
 * <p>检查项来自下单快照，创建履约记录时复制；完成动作条件更新
 * （未完成 → 已完成），重复完成幂等返回原记录。</p>
 */
@Mapper
public interface ServiceChecklistItemMapper extends BaseMapper<ServiceChecklistItemEntity> {

    /**
     * 记录下全部检查项（sort_order 升序）。
     *
     * @param serviceRecordId 履约记录 ID
     * @return 检查项列表
     */
    @Select("SELECT * FROM service_checklist_item "
            + "WHERE service_record_id = #{serviceRecordId} ORDER BY sort_order ASC")
    List<ServiceChecklistItemEntity> selectByRecordId(@Param("serviceRecordId") Long serviceRecordId);

    /**
     * 完成检查项：completed 0 → 1 并记录时间与操作人。
     *
     * @param itemId    检查项 ID
     * @param accountId 操作人账号 ID
     * @param remark    备注（可空）
     * @return 影响行数，0 表示已完成
     */
    @Update("UPDATE service_checklist_item SET completed = 1, "
            + "completed_at = CURRENT_TIMESTAMP(3), completed_by_account_id = #{accountId}, "
            + "remark = #{remark}, version = version + 1 "
            + "WHERE id = #{itemId} AND completed = 0")
    int complete(@Param("itemId") Long itemId, @Param("accountId") Long accountId,
                 @Param("remark") String remark);
}
