package com.housekeeping.platform.customer.persistence.entity;

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
 * 客户服务地址，对应表 {@code customer_address}。
 *
 * <p>注意 {@code activeDefaultCustomerId} 是数据库生成列，实体里用
 * {@code @TableField(exist = false)} 排除：如果让 MyBatis-Plus 参与写入，
 * 会拼出 {@code INSERT ... active_default_customer_id = ?}，MySQL 直接报错
 * "The value specified for generated column ... is not allowed"。</p>
 */
@Data
@TableName("customer_address")
public class CustomerAddressEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String addressNo;

    private Long customerId;

    /** HOME / PARENTS / OFFICE / RENTAL / OTHER。 */
    private String label;

    private String contactName;

    private String contactCountryCode;

    private String contactPhone;

    private String provinceCode;

    private String provinceName;

    private String cityCode;

    private String cityName;

    private String districtCode;

    private String districtName;

    private String streetCode;

    private String streetName;

    private Long serviceAreaId;

    /** UNMATCHED / MATCHED / OUT_OF_RANGE / STALE。 */
    private String areaMatchStatus;

    private LocalDateTime areaMatchedAt;

    private String communityName;

    private String buildingNo;

    private String unitNo;

    private String floorNo;

    private String roomNo;

    private String detailAddress;

    private BigDecimal longitude;

    private BigDecimal latitude;

    /** 门禁、电梯、宠物等履约说明，按敏感字段管理。 */
    private String accessNote;

    private Boolean isDefault;

    /** ACTIVE / DELETED。 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime lastUsedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    /** 数据库生成列，仅用于唯一约束，不参与读写。 */
    @TableField(exist = false)
    private Long activeDefaultCustomerId;
}
