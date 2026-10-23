package com.housekeeping.platform.scheduling.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.scheduling.domain.SlotStatus;
import com.housekeeping.platform.scheduling.persistence.entity.WorkerSlotEntity;
import com.housekeeping.platform.scheduling.persistence.mapper.WorkerSlotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 人员时段锁定/确认/释放（跨模块契约，docs/07 第 11.6 节
 * {@code WorkerSlotReservationService}）。
 *
 * <p>订单模块（批次 3）在创建订单、支付成功、支付超时、取消订单四个时机调用本服务。
 * 三个铁律：</p>
 * <ol>
 *   <li><b>只做条件更新</b>：锁定/确认/释放全部由 Mapper 的 WHERE 状态条件保证，
 *       影响行数为 0 就抛业务异常，由订单事务回滚——两个订单抢同一时段只有一个成功；</li>
 *   <li><b>bookingId 必须带进条件</b>：释放与确认只作用于"自己锁的时段"，
 *       重复调用、迟到的回调都打不到别人的占用上；</li>
 *   <li><b>本服务不判断订单状态</b>：订单是否可取消/可确认是订单模块的规则，
 *       这里只认时段状态机。</li>
 * </ol>
 */
@Service
@RequiresPersistence
public class WorkerSlotReservationService {

    /** 待支付锁定的默认时长：支付超时由订单模块定义，时段只负责到期可释放。 */
    static final long DEFAULT_HOLD_MINUTES = 15;

    private final WorkerSlotMapper slotMapper;

    public WorkerSlotReservationService(WorkerSlotMapper slotMapper) {
        this.slotMapper = slotMapper;
    }

    /**
     * 锁定指定人员的空闲时段（下单时调用）。
     *
     * <p>按（人员, 日期, 时间窗）定位时段——订单知道自己要哪个时间窗，
     * 不需要知道时段表内部 ID。锁定失败抛 {@code WORKER_SLOT_NOT_AVAILABLE}，
     * 订单不得创建。</p>
     *
     * @param workerId  人员 ID
     * @param serviceDate 服务日期
     * @param windowId  时间窗 ID
     * @param bookingId 订单 ID
     * @return 锁定后的时段实体
     * @throws BusinessException 时段不存在、非 AVAILABLE 或并发竞争失败
     */
    @Transactional
    public WorkerSlotEntity hold(long workerId, LocalDate serviceDate, long windowId,
                                 long bookingId) {
        WorkerSlotEntity slot = slotMapper.selectOne(new LambdaQueryWrapper<WorkerSlotEntity>()
                .eq(WorkerSlotEntity::getWorkerId, workerId)
                .eq(WorkerSlotEntity::getServiceDate, serviceDate)
                .eq(WorkerSlotEntity::getWindowId, windowId));
        if (slot == null) {
            throw new BusinessException(ErrorCode.WORKER_SLOT_NOT_FOUND);
        }
        if (SlotStatus.of(slot.getStatus()) != SlotStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.WORKER_SLOT_NOT_AVAILABLE);
        }
        LocalDateTime holdExpiresAt = LocalDateTime.now().plusMinutes(DEFAULT_HOLD_MINUTES);
        // 条件更新是并发安全的唯一来源：版本不匹配即说明读与锁之间被人抢先
        if (slotMapper.hold(slot.getId(), bookingId, holdExpiresAt, slot.getVersion()) == 0) {
            throw new BusinessException(ErrorCode.WORKER_SLOT_NOT_AVAILABLE);
        }
        return slotMapper.selectById(slot.getId());
    }

    /**
     * 支付成功确认：HELD → BOOKED（支付回调事务内调用）。
     *
     * @param slotId    时段 ID
     * @param bookingId 订单 ID
     * @throws BusinessException 时段不是该订单的 HELD 状态
     */
    @Transactional
    public void confirm(long slotId, long bookingId) {
        if (slotMapper.confirm(slotId, bookingId) == 0) {
            // 释放/过期后再来确认属于时序异常，明确报错让支付侧走补偿
            throw new BusinessException(ErrorCode.WORKER_SLOT_RELEASE_CONFLICT);
        }
    }

    /**
     * 释放待支付锁定（未支付取消，不检查到期时间）。
     *
     * @param slotId    时段 ID
     * @param bookingId 订单 ID
     * @throws BusinessException 时段已被并发改变
     */
    @Transactional
    public void releaseHeld(long slotId, long bookingId) {
        if (slotMapper.releaseHeld(slotId, bookingId) == 0) {
            throw new BusinessException(ErrorCode.WORKER_SLOT_RELEASE_CONFLICT);
        }
    }

    /**
     * 释放已过期待支付锁定（定时任务批量清理）。
     *
     * <p>与 {@link #releaseHeld} 的差异：多了到期时间条件，
     * 未到期的锁定不会被任务碰掉。释放 0 行不算失败——可能刚被订单侧正常取消。</p>
     *
     * @param slotId    时段 ID
     * @param bookingId 订单 ID
     * @return 是否实际释放
     */
    @Transactional
    public boolean releaseExpiredHeld(long slotId, long bookingId) {
        return slotMapper.releaseExpiredHeld(slotId, bookingId) > 0;
    }

    /**
     * 释放已支付订单占用的时段（取消已支付订单）。
     *
     * <p>日期未过恢复 AVAILABLE 可再售；日期已过转 CLOSED。
     * "排班仍有效"的校验由调用方（订单模块）在取消流程里决定，这里只按日期二分。</p>
     *
     * @param slotId    时段 ID
     * @param bookingId 订单 ID
     * @throws BusinessException 时段已被并发改变
     */
    @Transactional
    public void releaseBooked(long slotId, long bookingId) {
        WorkerSlotEntity slot = slotMapper.selectById(slotId);
        if (slot == null) {
            throw new BusinessException(ErrorCode.WORKER_SLOT_NOT_FOUND);
        }
        int updated = slot.getServiceDate().isBefore(LocalDate.now())
                ? slotMapper.closeBooked(slotId, bookingId)
                : slotMapper.releaseBooked(slotId, bookingId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.WORKER_SLOT_RELEASE_CONFLICT);
        }
    }

    /**
     * 按订单号查当前有效占用（订单取消/改期时定位时段）。
     *
     * @param bookingId 订单 ID
     * @return 占用的时段，无占用返回 {@code null}
     */
    @Transactional(readOnly = true)
    public WorkerSlotEntity findByBooking(long bookingId) {
        return slotMapper.selectByBookingId(bookingId);
    }
}
