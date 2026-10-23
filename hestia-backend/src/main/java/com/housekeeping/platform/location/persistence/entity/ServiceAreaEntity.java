package com.housekeeping.platform.location.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 公司服务区域，对应表 {@code service_area}。
 *
 * <p>{@code boundaryGeojson} 首版留空：接地图后它只作为几何判断的输入，
 * 业务编号与匹配结果仍以本表为准，其他模块不得直接依赖地图 SDK。</p>
 */
@Data
@TableName("service_area")
public class ServiceAreaEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String areaNo;

    private String areaName;

    private String description;

    /** 城市行政区 ID，必须是 CITY 层级。 */
    private Long cityRegionId;

    /** DISTRICT_GROUP / STREET_GROUP / CUSTOM。 */
    private String areaType;

    /** 数值越大越优先。 */
    private Integer priority;

    /** DRAFT / ACTIVE / INACTIVE / ARCHIVED。 */
    private String status;

    private BigDecimal centerLongitude;

    private BigDecimal centerLatitude;

    private String boundaryGeojson;

    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveTo;

    @Version
    private Long version;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
