package com.housekeeping.platform.identity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.identity.persistence.entity.AccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 账号 Mapper。
 *
 * <p>为什么把"计数 +1"写成 SQL 而不是"读出来加一再写回"：
 * 并发登录失败时，读改写会丢失更新（两个请求都读到 4，都写回 5），
 * 于是"5 次失败锁定"永远触发不了，爆破防护形同虚设。
 * 交给数据库做 {@code failed_login_count = failed_login_count + 1} 才是原子的。</p>
 */
@Mapper
public interface AccountMapper extends BaseMapper<AccountEntity> {

    /**
     * 按归一化手机号查账号。
     *
     * @param countryCode 区号
     * @param phone       纯数字号码
     * @return 账号；不存在返回 {@code null}
     */
    @Select("SELECT * FROM account WHERE phone_country_code = #{countryCode} AND phone = #{phone} LIMIT 1")
    AccountEntity selectByPhone(@Param("countryCode") String countryCode, @Param("phone") String phone);

    /**
     * 按对外账号编号查账号。
     *
     * @param accountNo 账号编号
     * @return 账号；不存在返回 {@code null}
     */
    @Select("SELECT * FROM account WHERE account_no = #{accountNo} LIMIT 1")
    AccountEntity selectByAccountNo(@Param("accountNo") String accountNo);

    /**
     * 原子累加登录失败次数。
     *
     * @param id 账号 ID
     * @return 影响行数
     */
    @Update("UPDATE account SET failed_login_count = failed_login_count + 1 WHERE id = #{id}")
    int incrementFailedLoginCount(@Param("id") Long id);

    /**
     * 登录失败达到阈值时落锁定信息。
     *
     * @param id          账号 ID
     * @param failedCount 失败次数
     * @param lockedUntil 锁定截止时间
     * @return 影响行数
     */
    @Update("UPDATE account SET failed_login_count = #{failedCount}, locked_until = #{lockedUntil}, "
            + "status = 'LOCKED' WHERE id = #{id}")
    int lockAccount(@Param("id") Long id, @Param("failedCount") int failedCount,
                    @Param("lockedUntil") LocalDateTime lockedUntil);

    /**
     * 解除已到期的临时锁定。
     *
     * <p>条件里带 {@code locked_until <= NOW()}：只有真的到期才恢复，
     * 避免管理员手工设置的长期锁定被一次登录顺带清掉。</p>
     *
     * @param id 账号 ID
     * @return 影响行数；0 表示没有到期的锁
     */
    @Update("UPDATE account SET status = 'ACTIVE', failed_login_count = 0, locked_until = NULL "
            + "WHERE id = #{id} AND status = 'LOCKED' "
            + "AND locked_until IS NOT NULL AND locked_until <= CURRENT_TIMESTAMP(3)")
    int releaseExpiredLock(@Param("id") Long id);

    /**
     * 登录成功：清零失败计数并解除锁定。
     *
     * @param id 账号 ID
     * @return 影响行数
     */
    @Update("UPDATE account SET failed_login_count = 0, locked_until = NULL, "
            + "last_login_at = CURRENT_TIMESTAMP(3), "
            + "status = CASE WHEN status = 'LOCKED' THEN 'ACTIVE' ELSE status END "
            + "WHERE id = #{id}")
    int markLoginSuccess(@Param("id") Long id);

    /**
     * 令牌版本 +1，令该账号已签发的全部 Token 立即失效。
     *
     * @param id 账号 ID
     * @return 影响行数
     */
    @Update("UPDATE account SET token_version = token_version + 1 WHERE id = #{id}")
    int incrementTokenVersion(@Param("id") Long id);

    /**
     * 更新密码并让旧 Token 失效，同时清空锁定状态。
     *
     * @param id           账号 ID
     * @param passwordHash 新密码的 BCrypt 结果
     * @return 影响行数
     */
    @Update("UPDATE account SET password_hash = #{passwordHash}, token_version = token_version + 1, "
            + "failed_login_count = 0, locked_until = NULL, "
            + "status = CASE WHEN status = 'LOCKED' THEN 'ACTIVE' ELSE status END "
            + "WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    /**
     * 更新账号状态并令旧 Token 失效。
     *
     * @param id     账号 ID
     * @param status 目标状态
     * @return 影响行数
     */
    @Update("UPDATE account SET status = #{status}, token_version = token_version + 1, "
            + "failed_login_count = 0, locked_until = NULL WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 邀请激活：写入密码并把账号置为 ACTIVE。
     *
     * <p>单独一个方法而不是复用 {@link #updatePassword}：激活是从
     * {@code PENDING_ACTIVATION} 到 {@code ACTIVE} 的状态迁移，
     * 复用改密 SQL 会让账号"有密码但仍是待激活"，登录永远失败。</p>
     *
     * @param id           账号 ID
     * @param passwordHash 密码的 BCrypt 结果
     * @return 影响行数
     */
    @Update("UPDATE account SET password_hash = #{passwordHash}, status = 'ACTIVE', "
            + "token_version = token_version + 1, failed_login_count = 0, locked_until = NULL "
            + "WHERE id = #{id} AND status = 'PENDING_ACTIVATION'")
    int activateWithPassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);
}
