package com.housekeeping.platform.infrastructure.persistence.sequence;

import com.housekeeping.platform.common.persistence.RequiresPersistence;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 业务编号生成器：{@code 前缀 + yyyyMMdd + 6 位日序列}。
 *
 * <p>为什么不用"随机数"或"count(*) + 1"：</p>
 * <ul>
 *   <li>随机后缀不可读，客服无法口头核对单号；</li>
 *   <li>{@code count(*) + 1} 在并发下必然重复，且删除记录后会产生空洞与重号。</li>
 * </ul>
 *
 * <p>实现方式：数据库行级自增（{@code UPDATE ... current_value + 1}），
 * 首次取号时插入序列行，插入冲突说明别的请求抢先初始化，退化为再自增一次。</p>
 *
 * <p>已知取舍：并发下可能<b>跳号</b>（回滚或读到了别的请求推进后的值），
 * 但绝不会重号——唯一索引 {@code uk_customer_profile_no} 等还会再兜一层。
 * 对业务编号来说"唯一"是硬要求，"连续"不是。</p>
 */
@Component
@RequiresPersistence
public class BusinessNoGenerator {

    /** 业务日期统一按北京时间，与数据库 {@code DATETIME(3)} 的时区口径一致。 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final int SEQUENCE_WIDTH = 6;

    private final BusinessNoSequenceMapper sequenceMapper;

    public BusinessNoGenerator(BusinessNoSequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
    }

    /**
     * 生成业务编号。
     *
     * @param seqKey 业务类型，如 {@code CUSTOMER}
     * @param prefix 编号前缀，如 {@code CUS}
     * @return 形如 {@code CUS202610230001}
     */
    public String next(String seqKey, String prefix) {
        String seqDate = LocalDate.now(BUSINESS_ZONE).format(DATE_FORMAT);
        long value = nextValue(seqKey, seqDate);
        return prefix + seqDate + pad(value);
    }

    private long nextValue(String seqKey, String seqDate) {
        if (sequenceMapper.increment(seqKey, seqDate) > 0) {
            return readCurrent(seqKey, seqDate);
        }
        try {
            sequenceMapper.insertFirst(seqKey, seqDate);
            return 1L;
        } catch (DuplicateKeyException exception) {
            // 并发下另一个请求已完成初始化，本次退化为自增
            sequenceMapper.increment(seqKey, seqDate);
            return readCurrent(seqKey, seqDate);
        }
    }

    private long readCurrent(String seqKey, String seqDate) {
        Long current = sequenceMapper.currentValue(seqKey, seqDate);
        if (current == null) {
            throw new IllegalStateException("业务编号序列读取失败：" + seqKey + "/" + seqDate);
        }
        return current;
    }

    private String pad(long value) {
        String text = Long.toString(value);
        if (text.length() >= SEQUENCE_WIDTH) {
            return text;
        }
        return "0".repeat(SEQUENCE_WIDTH - text.length()) + text;
    }
}
