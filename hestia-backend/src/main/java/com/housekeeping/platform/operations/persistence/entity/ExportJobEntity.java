package com.housekeeping.platform.operations.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据导出任务，对应表 {@code export_job}（docs/modules/13 第 14.4 节）。
 *
 * <p>敏感数据导出必须异步、可审计、有过期时间；状态推进全部走 Mapper 的条件更新，
 * 不使用 {@code updateById}。{@code query_json} 是查询条件快照，重放时以快照为准，
 * 避免同一任务两次执行得到不同范围的数据。</p>
 */
@Data
@TableName("export_job")
public class ExportJobEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 导出业务编号，形如 EXP20261024000001。 */
    private String exportNo;

    /** 导出类型：ORDERS/WORKERS/PAYMENTS/AFTER_SALES/AUDIT。 */
    private String exportType;

    /** 查询条件快照（日期区间、区域等），禁止含敏感明文。 */
    private String queryJson;

    /** 状态机：PENDING/PROCESSING/SUCCESS/FAILED/EXPIRED/CANCELED。 */
    private String status;

    /** 发起账号 ID。 */
    private Long requestedByAccountId;

    /** file_object 主键，生成成功后回填。 */
    private Long fileId;

    /** 导出行数。 */
    private Integer rowCount;

    /** 失败原因（截断保存）。 */
    private String errorMessage;

    /** 文件过期时间，到期清理。 */
    private LocalDateTime expiresAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
