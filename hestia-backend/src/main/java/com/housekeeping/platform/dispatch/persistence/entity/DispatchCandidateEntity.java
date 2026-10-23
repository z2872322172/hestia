package com.housekeeping.platform.dispatch.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 派单候选人员快照，对应表 {@code dispatch_candidate}（docs/modules/08 第 13.3 节）。
 *
 * <p>唯一键 {@code (dispatch_job_id, worker_id)}：同一任务同一人员最多出现一次。
 * 评分快照不含客户地址或电话（docs 第 7 节）。</p>
 */
@Data
@TableName("dispatch_candidate")
public class DispatchCandidateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dispatchJobId;

    private Long policyId;

    private Long workerId;

    private Integer rankNo;

    private BigDecimal totalScore;

    /** 评分拆解 JSON。 */
    private String scoreJson;

    /** GENERATED / SELECTED / SKIPPED / EXPIRED。 */
    private String status;

    private LocalDateTime generatedAt;
}
