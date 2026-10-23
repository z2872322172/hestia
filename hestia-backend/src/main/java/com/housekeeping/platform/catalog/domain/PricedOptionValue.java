package com.housekeeping.platform.catalog.domain;

import java.math.BigDecimal;

/**
 * 参与计价的一个已选项。
 *
 * <p>这是"校验并解析后"的形态，不是数据库实体：价格与时长增量已经从配置里取出来，
 * {@link PriceCalculator} 只做算术，不再查库。这样计价逻辑可以被单元测试穷举，
 * 不需要启动 Spring，也不需要连 MySQL。</p>
 *
 * @param optionCode   规格组编码
 * @param optionName   规格组名称，用于生成计价说明
 * @param valueCode    规格值编码
 * @param valueName    规格值名称，用于生成计价说明
 * @param extraPrice   附加金额，非负
 * @param durationDelta 时长增量（分钟），可为负
 * @param numericValue 数值型规格值（面积、小时数等），可为 {@code null}
 */
public record PricedOptionValue(String optionCode, String optionName, String valueCode, String valueName,
                                BigDecimal extraPrice, int durationDelta, BigDecimal numericValue) {
}
