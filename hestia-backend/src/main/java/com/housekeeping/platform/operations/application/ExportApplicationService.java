package com.housekeeping.platform.operations.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.housekeeping.platform.booking.application.contract.BookingOperationsStatsService;
import com.housekeeping.platform.booking.persistence.query.BookingExportRow;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.identity.application.contract.AreaScope;
import com.housekeeping.platform.identity.application.contract.OperatorAreaScopeService;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.infrastructure.storage.minio.FileObject;
import com.housekeeping.platform.infrastructure.storage.minio.FileObjectMapper;
import com.housekeeping.platform.infrastructure.storage.minio.ObjectStoragePort;
import com.housekeeping.platform.operations.domain.ExportJobStatus;
import com.housekeeping.platform.operations.domain.ExportType;
import com.housekeeping.platform.operations.domain.OrderExportCsv;
import com.housekeeping.platform.operations.persistence.entity.ExportJobEntity;
import com.housekeeping.platform.operations.persistence.mapper.ExportJobMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据导出应用服务（docs/modules/13 第 11 节）。
 *
 * <p>流程：发起（校验权限与数据范围）→ 创建 export_job → 异步生成文件
 * （runOnce 由调度器 / 冒烟接口驱动）→ 上传私有对象存储 → 回填 file_id 与行数
 * → 下载短期签名 URL → 到期清理。首版仅开放订单导出；
 * 查询条件以创建时的快照（query_json）为准，重放结果可复现。</p>
 */
@Service
@RequiresPersistence
public class ExportApplicationService {

    /** 文件保留天数（expires_at），到期由清理逻辑刷成 EXPIRED。 */
    private static final int EXPORT_RETENTION_DAYS = 7;

    /** 单个导出文件的最大行数，防止全表拖垮内存。 */
    private static final int EXPORT_ROW_LIMIT = 10000;

    /** 报表区间上限（天）。 */
    private static final int MAX_RANGE_DAYS = 92;

    private final ExportJobMapper exportJobMapper;

    private final FileObjectMapper fileObjectMapper;

    private final ObjectProvider<ObjectStoragePort> storagePortProvider;

    private final BusinessNoGenerator noGenerator;

    private final ObjectMapper objectMapper;

    private final AuditRecorder auditRecorder;

    private final OperatorAreaScopeService areaScopeService;

    private final CurrentAccountResolver currentAccountResolver;

    private final BookingOperationsStatsService bookingStats;

    @Value("${app.storage.enabled:true}")
    private boolean storageEnabled;

    @Value("${app.storage.private-bucket:housekeeping-dev-private}")
    private String privateBucket;

    @Value("${app.storage.presigned-expiry-seconds:900}")
    private long presignedExpirySeconds;

    public ExportApplicationService(ExportJobMapper exportJobMapper,
                                    FileObjectMapper fileObjectMapper,
                                    ObjectProvider<ObjectStoragePort> storagePortProvider,
                                    BusinessNoGenerator noGenerator,
                                    ObjectMapper objectMapper,
                                    AuditRecorder auditRecorder,
                                    OperatorAreaScopeService areaScopeService,
                                    CurrentAccountResolver currentAccountResolver,
                                    BookingOperationsStatsService bookingStats) {
        this.exportJobMapper = exportJobMapper;
        this.fileObjectMapper = fileObjectMapper;
        this.storagePortProvider = storagePortProvider;
        this.noGenerator = noGenerator;
        this.objectMapper = objectMapper;
        this.auditRecorder = auditRecorder;
        this.areaScopeService = areaScopeService;
        this.currentAccountResolver = currentAccountResolver;
        this.bookingStats = bookingStats;
    }

    /**
     * 导出任务视图。
     *
     * @param exportNo     导出编号
     * @param exportType   导出类型
     * @param status       状态
     * @param rowCount     行数（成功后回填）
     * @param errorMessage 失败原因
     * @param expiresAt    文件过期时间
     * @param createdAt    创建时间
     */
    public record ExportJobView(String exportNo, String exportType, String status,
                                Integer rowCount, String errorMessage,
                                LocalDateTime expiresAt, LocalDateTime createdAt) {
    }

    /**
     * 下载地址视图。
     *
     * @param url             预签名 URL
     * @param expiresInSeconds 有效期（秒）
     */
    public record DownloadUrlView(String url, long expiresInSeconds) {
    }

    /**
     * 发起导出。
     *
     * @param type   导出类型
     * @param from   起始日期（含）
     * @param to     结束日期（含）
     * @param reason 导出原因（写入审计）
     * @return 导出任务视图
     */
    @Transactional
    public ExportJobView create(ExportType type, LocalDate from, LocalDate to, String reason) {
        AreaScope scope = requireScope();
        if (type == null || !type.isExportable()) {
            throw new BusinessException(ErrorCode.REPORT_EXPORT_NOT_ALLOWED);
        }
        validateRange(from, to);
        ExportJobEntity entity = new ExportJobEntity();
        entity.setExportNo(noGenerator.next("EXPORT_JOB", "EXP"));
        entity.setExportType(type.name());
        entity.setQueryJson(snapshotQuery(scope, from, to));
        entity.setStatus(ExportJobStatus.PENDING.name());
        entity.setRequestedByAccountId(currentAccountResolver.requireAccountId());
        exportJobMapper.insert(entity);
        auditRecorder.record("EXPORT_REQUEST", "EXPORT_JOB", entity.getExportNo(), null,
                "{\"exportType\":\"" + type.name() + "\",\"from\":\"" + from + "\",\"to\":\"" + to + "\"}",
                reason);
        return toView(entity);
    }

    /**
     * 导出任务分页（运营看自己的，管理员看全部）。
     *
     * @param status 状态过滤（可空）
     * @param page   页码
     * @param size   条数
     * @return 分页任务
     */
    @Transactional(readOnly = true)
    public PageResult<ExportJobView> page(String status, int page, int size) {
        boolean admin = "ADMIN".equals(currentAccountResolver.currentRoleCode());
        LambdaQueryWrapper<ExportJobEntity> wrapper = new LambdaQueryWrapper<ExportJobEntity>()
                .eq(status != null && !status.isBlank(), ExportJobEntity::getStatus, status)
                .eq(!admin, ExportJobEntity::getRequestedByAccountId,
                        currentAccountResolver.requireAccountId())
                .orderByDesc(ExportJobEntity::getId);
        Page<ExportJobEntity> result = exportJobMapper.selectPage(new Page<>(page, size), wrapper);
        List<ExportJobView> records = result.getRecords().stream()
                .map(ExportApplicationService::toView)
                .toList();
        return new PageResult<>(records, (int) result.getCurrent(), (int) result.getSize(),
                result.getTotal());
    }

    /**
     * 导出任务详情。
     *
     * @param exportNo 导出编号
     * @return 任务视图
     */
    @Transactional(readOnly = true)
    public ExportJobView detail(String exportNo) {
        return toView(requireOwned(exportNo));
    }

    /**
     * 生成下载地址（短期签名 URL，每次访问前重新鉴权）。
     *
     * @param exportNo 导出编号
     * @return 下载地址视图
     */
    @Transactional(readOnly = true)
    public DownloadUrlView downloadUrl(String exportNo) {
        ExportJobEntity entity = requireOwned(exportNo);
        ExportJobStatus status = ExportJobStatus.valueOf(entity.getStatus());
        if (status == ExportJobStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.EXPORT_JOB_EXPIRED);
        }
        if (status != ExportJobStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.EXPORT_FILE_NOT_READY);
        }
        if (entity.getExpiresAt() != null
                && entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.EXPORT_JOB_EXPIRED);
        }
        if (entity.getFileId() == null) {
            throw new BusinessException(ErrorCode.EXPORT_FILE_NOT_READY);
        }
        ObjectStoragePort storage = requireStorage();
        FileObject file = fileObjectMapper.selectById(entity.getFileId());
        if (file == null) {
            throw new BusinessException(ErrorCode.EXPORT_FILE_NOT_READY);
        }
        return new DownloadUrlView(
                storage.presignedGetUrl(file.getBucketName(), file.getObjectKey(),
                        Duration.ofSeconds(presignedExpirySeconds)),
                presignedExpirySeconds);
    }

    /**
     * 取消待处理的导出任务。
     *
     * @param exportNo 导出编号
     * @return 取消后的任务视图
     */
    @Transactional
    public ExportJobView cancel(String exportNo) {
        ExportJobEntity entity = requireOwned(exportNo);
        ExportJobStatus status = ExportJobStatus.valueOf(entity.getStatus());
        if (!status.canCancel()) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "当前状态不允许取消");
        }
        if (exportJobMapper.cancelPending(entity.getId()) == 0) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "任务已被处理，取消失败");
        }
        auditRecorder.record("EXPORT_CANCEL", "EXPORT_JOB", exportNo, null,
                "{\"status\":\"CANCELED\"}", null);
        return toView(exportJobMapper.selectByExportNo(exportNo));
    }

    /**
     * 处理一批待导出任务（调度器 / 冒烟接口驱动）。
     *
     * <p>方法本身不加事务：认领（claim）必须先落库再处理，失败时 markFailed
     * 也要独立提交；文件元数据与任务完成状态之间的极端不一致（文件已入库、
     * 任务标记失败）由"重新发起导出"兜底，不影响正确性。</p>
     *
     * @param asOf      时间基准（供模拟时钟测试）
     * @param batchSize 单批处理数
     * @return 本次成功生成的任务数
     */
    public int runOnce(LocalDateTime asOf, int batchSize) {
        exportJobMapper.expireDue(asOf);
        if (!storageEnabled) {
            return 0;
        }
        ObjectStoragePort storage = storagePortProvider.getIfAvailable();
        if (storage == null) {
            return 0;
        }
        List<Long> ids = exportJobMapper.selectList(new LambdaQueryWrapper<ExportJobEntity>()
                        .eq(ExportJobEntity::getStatus, ExportJobStatus.PENDING.name())
                        .orderByAsc(ExportJobEntity::getId)
                        .last("LIMIT " + Math.max(1, batchSize)))
                .stream()
                .map(ExportJobEntity::getId)
                .toList();
        int processed = 0;
        for (Long id : ids) {
            if (exportJobMapper.claim(id) == 0) {
                continue;
            }
            ExportJobEntity job = exportJobMapper.selectById(id);
            try {
                process(job, storage, asOf);
                processed++;
            } catch (Exception exception) {
                exportJobMapper.markFailed(id, truncate(exception.getMessage(), 500));
            }
        }
        return processed;
    }

    private void process(ExportJobEntity job, ObjectStoragePort storage, LocalDateTime asOf)
            throws Exception {
        JsonNode query = objectMapper.readTree(job.getQueryJson());
        LocalDate from = LocalDate.parse(query.path("from").asText());
        LocalDate to = LocalDate.parse(query.path("to").asText());
        List<Long> areaIds = null;
        if (query.has("areaIds")) {
            areaIds = new ArrayList<>();
            for (JsonNode node : query.get("areaIds")) {
                areaIds.add(node.asLong());
            }
        }
        List<BookingExportRow> rows = bookingStats.exportRows(areaIds, from, to, EXPORT_ROW_LIMIT);
        byte[] bytes = OrderExportCsv.assemble(rows).getBytes(StandardCharsets.UTF_8);
        String objectKey = "exports/" + job.getExportNo() + ".csv";
        storage.putObject(privateBucket, objectKey, new ByteArrayInputStream(bytes),
                bytes.length, "text/csv");

        FileObject file = new FileObject();
        file.setBucketName(privateBucket);
        file.setObjectKey(objectKey);
        file.setOriginalName(job.getExportNo() + ".csv");
        file.setContentType("text/csv");
        file.setSizeBytes((long) bytes.length);
        file.setVisibility("PRIVATE");
        file.setOwnerAccountId(job.getRequestedByAccountId());
        file.setBusinessType("EXPORT_JOB");
        file.setBusinessId(job.getExportNo());
        file.setStatus("BOUND");
        fileObjectMapper.insert(file);

        if (exportJobMapper.finish(job.getId(), file.getId(), rows.size(),
                asOf.plus(EXPORT_RETENTION_DAYS, ChronoUnit.DAYS)) == 0) {
            throw new IllegalStateException("导出任务状态已变化：" + job.getExportNo());
        }
    }

    private ObjectStoragePort requireStorage() {
        ObjectStoragePort storage = storagePortProvider.getIfAvailable();
        if (storage == null || !storageEnabled) {
            throw new BusinessException(ErrorCode.EXPORT_FILE_NOT_READY);
        }
        return storage;
    }

    private ExportJobEntity requireOwned(String exportNo) {
        ExportJobEntity entity = exportJobMapper.selectByExportNo(exportNo);
        if (entity == null) {
            throw new BusinessException(ErrorCode.EXPORT_JOB_NOT_FOUND);
        }
        boolean admin = "ADMIN".equals(currentAccountResolver.currentRoleCode());
        if (!admin && !Long.valueOf(currentAccountResolver.requireAccountId())
                .equals(entity.getRequestedByAccountId())) {
            throw new BusinessException(ErrorCode.EXPORT_JOB_NOT_OWNED);
        }
        return entity;
    }

    private AreaScope requireScope() {
        long accountId = currentAccountResolver.requireAccountId();
        AreaScope scope = areaScopeService.resolveAreaScope(accountId,
                currentAccountResolver.currentRoleCode());
        if (!scope.allAreas() && scope.areaIds().isEmpty()) {
            throw new BusinessException(ErrorCode.DASHBOARD_DATA_FORBIDDEN);
        }
        return scope;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)
                || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.REPORT_RANGE_INVALID);
        }
    }

    /** 查询条件快照：日期区间 + 授权区域（ADMIN 记 allAreas，不记区域列表）。 */
    private String snapshotQuery(AreaScope scope, LocalDate from, LocalDate to) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("from", from.toString());
        node.put("to", to.toString());
        if (scope.allAreas()) {
            node.put("allAreas", true);
        } else {
            ArrayNode areaIds = node.putArray("areaIds");
            scope.areaIds().forEach(areaIds::add);
        }
        return node.toString();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static ExportJobView toView(ExportJobEntity entity) {
        return new ExportJobView(entity.getExportNo(), entity.getExportType(), entity.getStatus(),
                entity.getRowCount(), entity.getErrorMessage(), entity.getExpiresAt(),
                entity.getCreatedAt());
    }
}
