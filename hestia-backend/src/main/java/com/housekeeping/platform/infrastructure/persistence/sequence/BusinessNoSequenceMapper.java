package com.housekeeping.platform.infrastructure.persistence.sequence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 业务编号日序列 Mapper（表 {@code business_no_sequence}）。
 */
@Mapper
public interface BusinessNoSequenceMapper {

    /**
     * 当日序列 +1。
     *
     * @param seqKey 业务类型
     * @param seqDate 日期 yyyyMMdd
     * @return 影响行数；0 表示当天还没有序列行
     */
    @Update("UPDATE business_no_sequence SET current_value = current_value + 1 "
            + "WHERE seq_key = #{seqKey} AND seq_date = #{seqDate}")
    int increment(@Param("seqKey") String seqKey, @Param("seqDate") String seqDate);

    /**
     * 初始化当天序列，第一条编号为 1。
     *
     * @param seqKey  业务类型
     * @param seqDate 日期 yyyyMMdd
     * @return 影响行数
     */
    @Insert("INSERT INTO business_no_sequence (seq_key, seq_date, current_value) "
            + "VALUES (#{seqKey}, #{seqDate}, 1)")
    int insertFirst(@Param("seqKey") String seqKey, @Param("seqDate") String seqDate);

    /**
     * 读取当前值。
     *
     * @param seqKey  业务类型
     * @param seqDate 日期 yyyyMMdd
     * @return 当前值；无记录返回 {@code null}
     */
    @Select("SELECT current_value FROM business_no_sequence WHERE seq_key = #{seqKey} AND seq_date = #{seqDate}")
    Long currentValue(@Param("seqKey") String seqKey, @Param("seqDate") String seqDate);
}
