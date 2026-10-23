package com.housekeeping.platform.workforce.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 人员证书，对应表 {@code worker_certificate}。
 *
 * <p>证书编号存两列：{@code certificateNoCipher} 是密文（运营查看需解密权限），
 * {@code certificateNoHash} 是 HMAC 摘要，只用于"同一人同一类型不能重复登记同一张证"的唯一约束。
 * 不存明文是因为证书编号本身就是敏感个人信息，泄漏后可被用于伪造材料；
 * 只存哈希则无法回显给本人核对，所以两者都要。</p>
 *
 * <p>附件只存 {@code fileId}，不存 URL：私有桶的访问必须每次都走鉴权与预签名，
 * 存下来的 URL 迟早会过期，也可能被人转发出去。</p>
 */
@Data
@TableName("worker_certificate")
public class WorkerCertificateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workerId;

    /** IDENTITY / HEALTH / BACKGROUND_CHECK / SKILL_CERTIFICATE / SPECIAL_EQUIPMENT / OTHER。 */
    private String certificateType;

    /** 证书编号密文。 */
    private byte[] certificateNoCipher;

    /** 证书编号 HMAC-SHA256（hex，64 位），用于重复校验。 */
    private String certificateNoHash;

    private Long fileId;

    private LocalDate issuedAt;

    /** 到期日，{@code null} 表示长期有效。 */
    private LocalDate expiresAt;

    /** PENDING / APPROVED / REJECTED。 */
    private String auditStatus;

    /** VALID / EXPIRED / REVOKED。 */
    private String validStatus;

    private Long verifiedByAccountId;

    private LocalDateTime verifiedAt;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
