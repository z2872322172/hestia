package com.housekeeping.platform.infrastructure.persistence.idempotency;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 接口幂等服务（docs/07 第 12.5 节，供"创建订单"等写接口使用）。
 *
 * <p>三段式协议，调用方（Controller/应用服务）负责：</p>
 *
 * <pre>
 * 1. {@link #findSucceededResponse}：同键 + 同内容且已成功 → 直接回放历史响应；
 * 2. {@link #begin}：插入 PROCESSING 占位，唯一索引兜底并发；
 *    同键但内容不同、或同键请求仍在处理中 → IDEMPOTENCY_CONFLICT；
 * 3. 业务成功后 {@link #markSucceeded} 保存响应快照。
 * </pre>
 *
 * <p>为什么幂等记录不带失败回滚：业务事务回滚时占位行也会一起回滚
 * （占位插入与业务写库在同一事务），客户端用同键重试即可。</p>
 */
@Service
@RequiresPersistence
public class IdempotencyService {

    /**
     * 记录保留时长。回放窗口内重复请求直接返回历史响应；
     * 过期行由清理任务删除，不参与唯一键判断。
     */
    private static final Duration RECORD_TTL = Duration.ofHours(24);

    private final IdempotencyRecordMapper recordMapper;

    public IdempotencyService(IdempotencyRecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    /**
     * 查询可回放的成功响应。
     *
     * <p>同键 + 同账号 + 同接口且内容一致才回放；同键但内容不同直接冲突，
     * 防止客户端误用键把新请求静默替换成旧响应（docs/07 第 12.5 节）。</p>
     *
     * @param accountId      发起账号 ID（隔离不同用户的同名键）
     * @param apiPath        接口路径（防止同一键跨接口复用）
     * @param idempotencyKey 客户端幂等键
     * @param requestHash    本次请求内容摘要
     * @return 成功响应 JSON；无可回放记录返回空
     */
    public Optional<String> findSucceededResponse(Long accountId, String apiPath,
                                                  String idempotencyKey, String requestHash) {
        IdempotencyRecordEntity record = recordMapper.selectByKey(idempotencyKey);
        if (record == null || !"SUCCEEDED".equals(record.getStatus())) {
            return Optional.empty();
        }
        if (!accountId.equals(record.getAccountId()) || !apiPath.equals(record.getApiPath())) {
            // 键被其他用户或接口占用：不当冲突也不回放，走正常流程由唯一索引拦截
            return Optional.empty();
        }
        if (!requestHash.equals(record.getRequestHash())) {
            // 同键同用户同接口但内容不同：真冲突，绝不能回放旧响应
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return Optional.ofNullable(record.getResponseJson());
    }

    /**
     * 插入 PROCESSING 占位，开始幂等保护。
     *
     * @param accountId      发起账号 ID
     * @param apiPath        接口路径
     * @param idempotencyKey 客户端幂等键
     * @param requestHash    请求内容摘要
     */
    public void begin(Long accountId, String apiPath, String idempotencyKey, String requestHash) {
        IdempotencyRecordEntity record = new IdempotencyRecordEntity();
        record.setIdempotencyKey(idempotencyKey);
        record.setAccountId(accountId);
        record.setApiPath(apiPath);
        record.setRequestHash(requestHash);
        record.setStatus("PROCESSING");
        record.setExpiresAt(LocalDateTime.now().plus(RECORD_TTL));
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException exception) {
            throw describeConflict(idempotencyKey, requestHash);
        }
    }

    /**
     * 业务成功后保存响应快照（必须与业务写库同一事务）。
     *
     * @param idempotencyKey 客户端幂等键
     * @param responseJson   响应 JSON
     */
    public void markSucceeded(String idempotencyKey, String responseJson) {
        IdempotencyRecordEntity update = new IdempotencyRecordEntity();
        update.setStatus("SUCCEEDED");
        update.setResponseJson(responseJson);
        recordMapper.update(update,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IdempotencyRecordEntity>()
                        .eq(IdempotencyRecordEntity::getIdempotencyKey, idempotencyKey)
                        .eq(IdempotencyRecordEntity::getStatus, "PROCESSING"));
    }

    /**
     * 计算请求内容摘要（SHA-256 十六进制）。
     *
     * @param requestBody 请求体文本
     * @return 64 位十六进制摘要
     */
    public static String hashRequest(String requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(requestBody.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest unavailable", exception);
        }
    }

    /**
     * 区分两种冲突：同键不同内容（真冲突）与同键同内容仍在处理中（并发重放）。
     */
    private BusinessException describeConflict(String idempotencyKey, String requestHash) {
        IdempotencyRecordEntity existing = recordMapper.selectByKey(idempotencyKey);
        if (existing != null && requestHash.equals(existing.getRequestHash())
                && "PROCESSING".equals(existing.getStatus())) {
            return new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "相同请求正在处理中，请稍后查询结果");
        }
        return new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
    }
}
