package com.housekeeping.platform.scheduling.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.scheduling.application.command.CreateServiceWindowCommand;
import com.housekeeping.platform.scheduling.application.command.UpdateServiceWindowCommand;
import com.housekeeping.platform.scheduling.application.view.ServiceWindowView;
import com.housekeeping.platform.scheduling.domain.WindowTimeline;
import com.housekeeping.platform.scheduling.persistence.entity.ServiceWindowEntity;
import com.housekeeping.platform.scheduling.persistence.mapper.ServiceWindowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 标准服务时间窗服务。
 *
 * <p>时间窗是公司级配置，数量少、变化低频。重叠校验在应用层完成：
 * 数据库不做"时间不重叠"约束，因为校验需要与修改入口一致，
 * 放在 SQL 层会让"哪两个窗口撞了"这种问题只能靠报错猜测。</p>
 */
@Service
@RequiresPersistence
public class ServiceWindowService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private static final String STATUS_DISABLED = "DISABLED";

    private final ServiceWindowMapper windowMapper;

    public ServiceWindowService(ServiceWindowMapper windowMapper) {
        this.windowMapper = windowMapper;
    }

    /**
     * 新建时间窗。
     *
     * @param command 创建命令
     * @return 时间窗视图
     * @throws BusinessException 编码重复或时间区间与现有窗口重叠
     */
    @Transactional
    public ServiceWindowView create(CreateServiceWindowCommand command) {
        if (!WindowTimeline.validSpan(command.startTime(), command.endTime())) {
            throw new BusinessException(ErrorCode.SERVICE_WINDOW_OVERLAP);
        }
        if (windowMapper.selectCount(new LambdaQueryWrapper<ServiceWindowEntity>()
                .eq(ServiceWindowEntity::getWindowCode, command.windowCode())) > 0) {
            throw new BusinessException(ErrorCode.SERVICE_WINDOW_OVERLAP);
        }
        assertNoOverlap(null, command.startTime(), command.endTime());
        ServiceWindowEntity entity = new ServiceWindowEntity();
        entity.setWindowCode(command.windowCode());
        entity.setWindowName(command.windowName());
        entity.setStartTime(command.startTime());
        entity.setEndTime(command.endTime());
        entity.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        entity.setStatus(STATUS_ACTIVE);
        windowMapper.insert(entity);
        return toView(entity);
    }

    /**
     * 修改时间窗。
     *
     * <p>只影响后续生成的时段，存量排班与时段不回溯
     * （docs/modules/06 第 19.2 节：配置变更不自动改变已生成时段）。</p>
     *
     * @param windowId 时间窗 ID
     * @param command  修改命令
     * @return 时间窗视图
     * @throws BusinessException 不存在、并发版本冲突或时间区间与其他窗口重叠
     */
    @Transactional
    public ServiceWindowView update(long windowId, UpdateServiceWindowCommand command) {
        ServiceWindowEntity entity = windowMapper.selectById(windowId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SERVICE_WINDOW_NOT_FOUND);
        }
        if (!WindowTimeline.validSpan(command.startTime(), command.endTime())) {
            throw new BusinessException(ErrorCode.SERVICE_WINDOW_OVERLAP);
        }
        assertNoOverlap(windowId, command.startTime(), command.endTime());
        if (command.windowName() != null) {
            entity.setWindowName(command.windowName());
        }
        entity.setStartTime(command.startTime());
        entity.setEndTime(command.endTime());
        if (command.sortOrder() != null) {
            entity.setSortOrder(command.sortOrder());
        }
        entity.setStatus(STATUS_DISABLED.equals(command.status()) ? STATUS_DISABLED : STATUS_ACTIVE);
        entity.setVersion(command.expectedVersion());
        if (windowMapper.updateById(entity) == 0) {
            throw new BusinessException(ErrorCode.SERVICE_WINDOW_VERSION_CONFLICT);
        }
        return toView(entity);
    }

    /**
     * 运营视角：全部时间窗（含停用）。
     *
     * @return 时间窗列表
     */
    @Transactional(readOnly = true)
    public List<ServiceWindowView> listForOperator() {
        return windowMapper.selectList(new LambdaQueryWrapper<ServiceWindowEntity>()
                        .orderByAsc(ServiceWindowEntity::getSortOrder))
                .stream().map(this::toView).toList();
    }

    /**
     * 公开视角：仅启用的时间窗。
     *
     * @return 时间窗列表
     */
    @Transactional(readOnly = true)
    public List<ServiceWindowView> listActive() {
        return windowMapper.selectList(new LambdaQueryWrapper<ServiceWindowEntity>()
                        .eq(ServiceWindowEntity::getStatus, STATUS_ACTIVE)
                        .orderByAsc(ServiceWindowEntity::getSortOrder))
                .stream().map(this::toView).toList();
    }

    /**
     * 按编码取启用中的时间窗（booking 下单指定时间窗使用）。
     *
     * @param windowCode 时间窗编码，如 MORNING
     * @return 时间窗视图
     * @throws BusinessException 不存在或已停用
     */
    @Transactional(readOnly = true)
    public ServiceWindowView requireActiveByCode(String windowCode) {
        ServiceWindowEntity entity = windowMapper.selectOne(new LambdaQueryWrapper<ServiceWindowEntity>()
                .eq(ServiceWindowEntity::getWindowCode, windowCode));
        if (entity == null) {
            throw new BusinessException(ErrorCode.SERVICE_WINDOW_NOT_FOUND);
        }
        if (!STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.SERVICE_WINDOW_DISABLED);
        }
        return toView(entity);
    }

    /**
     * 按 ID 取启用中的时间窗（排班与可用性内部使用）。
     *
     * @param windowId 时间窗 ID
     * @return 实体
     * @throws BusinessException 不存在或已停用
     */
    @Transactional(readOnly = true)
    public ServiceWindowEntity requireActiveById(long windowId) {
        ServiceWindowEntity entity = windowMapper.selectById(windowId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SERVICE_WINDOW_NOT_FOUND);
        }
        if (!STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.SERVICE_WINDOW_DISABLED);
        }
        return entity;
    }

    /**
     * 按 ID 集合批量取时间窗实体（视图组装用，不校验状态）。
     *
     * @param windowIds 时间窗 ID 集合
     * @return 实体列表
     */
    @Transactional(readOnly = true)
    public List<ServiceWindowEntity> listByIds(java.util.Collection<Long> windowIds) {
        if (windowIds == null || windowIds.isEmpty()) {
            return List.of();
        }
        return windowMapper.selectBatchIds(windowIds);
    }

    /**
     * 按 ID 取时间窗实体（不校验状态，批量生成排班前逐个校验会重复报错，这里只负责存在性）。
     *
     * @param windowId 时间窗 ID
     * @return 实体
     * @throws BusinessException 不存在
     */
    @Transactional(readOnly = true)
    public ServiceWindowEntity requireById(long windowId) {
        ServiceWindowEntity entity = windowMapper.selectById(windowId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SERVICE_WINDOW_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 校验时间区间不与任何现有窗口重叠。
     *
     * @param excludeWindowId 修改场景排除自身，新建传 {@code null}
     * @param startTime       开始时间
     * @param endTime         结束时间
     * @throws BusinessException 与现有窗口重叠
     */
    private void assertNoOverlap(Long excludeWindowId, java.time.LocalTime startTime,
                                 java.time.LocalTime endTime) {
        for (ServiceWindowEntity existing : windowMapper.selectList(null)) {
            if (existing.getId().equals(excludeWindowId)) {
                continue;
            }
            if (WindowTimeline.overlaps(startTime, endTime,
                    existing.getStartTime(), existing.getEndTime())) {
                throw new BusinessException(ErrorCode.SERVICE_WINDOW_OVERLAP);
            }
        }
    }

    private ServiceWindowView toView(ServiceWindowEntity entity) {
        // insert 后 MyBatis-Plus 只回填 id，version 列的数据库默认值不会写回实体
        long version = entity.getVersion() == null ? 0L : entity.getVersion();
        return new ServiceWindowView(entity.getId(), entity.getWindowCode(), entity.getWindowName(),
                entity.getStartTime(), entity.getEndTime(), entity.getSortOrder(),
                entity.getStatus(), version);
    }
}
