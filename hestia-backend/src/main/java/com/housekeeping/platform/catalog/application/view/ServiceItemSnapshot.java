package com.housekeeping.platform.catalog.application.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 下单用的服务项目快照（docs/modules/04 第 13 节）。
 *
 * <p>订单模块拿到这份快照后<b>不再回查目录表</b>：服务改名、改价、下架、标准调整，
 * 都不能让历史订单的展示金额和履约检查项发生变化。</p>
 *
 * <p>刻意包含 {@code serviceStandardItems}：履约模块要照着下单那一刻的检查项逐条打勾，
 * 如果它读的是"当前模板"，运营中途加一条检查项，已支付订单的验收标准就被偷偷改了。</p>
 *
 * @param serviceItemId      服务项目 ID
 * @param serviceItemNo      服务项目编号
 * @param serviceItemName    服务项目名称
 * @param serviceItemVersion 服务项目版本
 * @param serviceUnit        销售单位
 * @param pricingMode        计价模式
 * @param durationMinutes    预计时长
 * @param basePrice          基础价
 * @param serviceStandardSummary 标准摘要
 * @param serviceStandardItems   标准检查项快照
 * @param snapshotAt         快照生成时间
 */
public record ServiceItemSnapshot(long serviceItemId, String serviceItemNo, String serviceItemName,
                                  long serviceItemVersion, String serviceUnit, String pricingMode,
                                  int durationMinutes, BigDecimal basePrice,
                                  String serviceStandardSummary,
                                  List<StandardSnapshot> serviceStandardItems,
                                  LocalDateTime snapshotAt) {

    /**
     * 标准检查项快照。
     *
     * @param standardGroup    分组
     * @param itemName         名称
     * @param description      说明
     * @param required         是否必做
     * @param evidenceRequired 是否必须上传照片
     * @param sortOrder        排序
     */
    public record StandardSnapshot(String standardGroup, String itemName, String description,
                                   boolean required, boolean evidenceRequired, int sortOrder) {
    }
}
