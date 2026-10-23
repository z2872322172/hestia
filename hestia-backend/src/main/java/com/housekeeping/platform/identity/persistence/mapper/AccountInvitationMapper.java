package com.housekeeping.platform.identity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.identity.persistence.entity.AccountInvitationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 邀请凭证 Mapper。
 */
@Mapper
public interface AccountInvitationMapper extends BaseMapper<AccountInvitationEntity> {

    /**
     * 按凭证哈希查询。
     *
     * @param tokenHash 凭证 SHA-256（小写十六进制）
     * @return 邀请记录；不存在返回 {@code null}
     */
    @Select("SELECT * FROM account_invitation WHERE token_hash = #{tokenHash} LIMIT 1")
    AccountInvitationEntity selectByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 消费一次性凭证。
     *
     * <p>条件更新是这里唯一正确的做法：先查后改会出现"两个请求都查到 PENDING、
     * 都判断未过期、都去设置密码"，导致同一邀请被激活两次。
     * 影响行数为 1 才说明本次真正抢到了凭证。</p>
     *
     * @param id 邀请 ID
     * @return 影响行数（1 表示消费成功）
     */
    @Update("UPDATE account_invitation SET status = 'USED', used_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'PENDING' AND expires_at > CURRENT_TIMESTAMP(3)")
    int consume(@Param("id") Long id);

    /**
     * 作废账号在指定用途下的所有未使用凭证（重新发邀请前调用）。
     *
     * @param accountId 账号 ID
     * @param purpose   用途
     * @return 影响行数
     */
    @Update("UPDATE account_invitation SET status = 'REVOKED' "
            + "WHERE account_id = #{accountId} AND purpose = #{purpose} AND status = 'PENDING'")
    int revokePending(@Param("accountId") Long accountId, @Param("purpose") String purpose);

    /**
     * 查询账号在指定用途下仍有效的凭证。
     *
     * @param accountId 账号 ID
     * @param purpose   用途
     * @return 凭证列表
     */
    @Select("SELECT * FROM account_invitation WHERE account_id = #{accountId} AND purpose = #{purpose} "
            + "AND status = 'PENDING' AND expires_at > CURRENT_TIMESTAMP(3) ORDER BY id DESC")
    List<AccountInvitationEntity> selectUsable(@Param("accountId") Long accountId,
                                               @Param("purpose") String purpose);
}
