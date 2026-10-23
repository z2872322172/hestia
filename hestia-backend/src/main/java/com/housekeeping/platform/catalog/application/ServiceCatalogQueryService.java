package com.housekeeping.platform.catalog.application;

import com.housekeeping.platform.catalog.application.view.OptionView;
import com.housekeeping.platform.catalog.application.view.ServiceItemDetailView;
import com.housekeeping.platform.catalog.application.view.ServiceItemRef;
import com.housekeeping.platform.catalog.application.view.ServiceItemSnapshot;
import com.housekeeping.platform.catalog.application.view.ServiceItemSummaryView;
import com.housekeeping.platform.catalog.application.view.StandardItemView;
import com.housekeeping.platform.catalog.application.view.TierView;
import com.housekeeping.platform.catalog.domain.PricingMode;
import com.housekeeping.platform.catalog.persistence.entity.ServiceCategoryEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemOptionEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemOptionValueEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServicePriceTierEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServiceStandardItemEntity;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceCategoryMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceItemMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceItemOptionMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceItemOptionValueMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServicePriceTierMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceStandardItemMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务目录查询（跨模块公开契约，docs/07 第 11.4 节 {@code ServiceCatalogQueryService}）。
 *
 * <p>前台只认 {@code PUBLISHED}：草稿与已下架项目对消费者与"不存在"完全等价，
 * 连"存在但不可买"都不该暴露——那会让下架变成一种可探测的信号。</p>
 */
@Service
@RequiresPersistence
public class ServiceCatalogQueryService {

    private final ServiceItemMapper itemMapper;

    private final ServiceCategoryMapper categoryMapper;

    private final ServiceItemOptionMapper optionMapper;

    private final ServiceItemOptionValueMapper optionValueMapper;

    private final ServicePriceTierMapper tierMapper;

    private final ServiceStandardItemMapper standardMapper;

    public ServiceCatalogQueryService(ServiceItemMapper itemMapper,
                                      ServiceCategoryMapper categoryMapper,
                                      ServiceItemOptionMapper optionMapper,
                                      ServiceItemOptionValueMapper optionValueMapper,
                                      ServicePriceTierMapper tierMapper,
                                      ServiceStandardItemMapper standardMapper) {
        this.itemMapper = itemMapper;
        this.categoryMapper = categoryMapper;
        this.optionMapper = optionMapper;
        this.optionValueMapper = optionValueMapper;
        this.tierMapper = tierMapper;
        this.standardMapper = standardMapper;
    }

    /**
     * 前台服务项目列表。
     *
     * @param categoryNo 分类编号，为空表示全部
     * @return 摘要列表
     * @throws BusinessException 指定分类不存在
     */
    @Transactional(readOnly = true)
    public List<ServiceItemSummaryView> listPublished(String categoryNo) {
        List<ServiceItemEntity> items;
        if (categoryNo == null || categoryNo.isBlank()) {
            items = itemMapper.selectAllPublished();
        } else {
            ServiceCategoryEntity category = categoryMapper.selectByCategoryNo(categoryNo);
            if (category == null) {
                throw new BusinessException(ErrorCode.SERVICE_CATEGORY_NOT_FOUND);
            }
            items = itemMapper.selectPublishedByCategory(category.getId());
        }

        // 分类表只有几十条，一次全取比逐条查省事且不会 N+1
        Map<Long, ServiceCategoryEntity> categories = new HashMap<>();
        for (ServiceCategoryEntity category : categoryMapper.selectAllOrdered()) {
            categories.put(category.getId(), category);
        }

        List<ServiceItemSummaryView> views = new ArrayList<>(items.size());
        for (ServiceItemEntity item : items) {
            views.add(toSummary(item, categories.get(item.getCategoryId())));
        }
        return views;
    }

    /**
     * 前台服务项目详情（仅已发布）。
     *
     * @param itemNo 服务项目编号
     * @return 详情
     * @throws BusinessException 不存在或不可售
     */
    @Transactional(readOnly = true)
    public ServiceItemDetailView detail(String itemNo) {
        ServiceItemEntity item = itemMapper.selectPublishedByItemNo(itemNo);
        if (item == null) {
            throw new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND);
        }
        return assembleDetail(item);
    }

    /**
     * 运营后台服务项目详情（任意状态）。
     *
     * @param itemNo 服务项目编号
     * @return 详情
     * @throws BusinessException 不存在
     */
    @Transactional(readOnly = true)
    public ServiceItemDetailView adminDetail(String itemNo) {
        return assembleDetail(requireItem(itemNo));
    }

    /**
     * 按编号取服务项目实体（任意状态，运营后台用）。
     *
     * @param itemNo 服务项目编号
     * @return 实体
     * @throws BusinessException 不存在
     */
    @Transactional(readOnly = true)
    public ServiceItemEntity requireItem(String itemNo) {
        ServiceItemEntity item = itemMapper.selectByItemNo(itemNo);
        if (item == null) {
            throw new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND);
        }
        return item;
    }

    /**
     * 按编号取服务项目摘要（跨模块契约）。
     *
     * <p>人员技能、资质要求、下单校验都需要"这个服务项目存不存在、现在什么状态"，
     * 但不需要价格阶梯和标准检查项。给它们实体意味着任何模块都能读到定价细节并自行算钱，
     * 定价逻辑就会在目录模块之外长出第二份实现。</p>
     *
     * @param itemNo 服务项目编号
     * @return 服务项目摘要
     * @throws BusinessException 不存在
     */
    @Transactional(readOnly = true)
    public ServiceItemRef requireItemRef(String itemNo) {
        return toRef(requireItem(itemNo));
    }

    /**
     * 按 ID 取服务项目摘要（跨模块契约）。
     *
     * <p>人员技能表存的是 {@code service_item_id}，派单与资格校验都要按 ID 反查名称。</p>
     *
     * @param serviceItemId 服务项目 ID
     * @return 服务项目摘要
     * @throws BusinessException 不存在
     */
    @Transactional(readOnly = true)
    public ServiceItemRef requireItemRefById(long serviceItemId) {
        ServiceItemEntity item = itemMapper.selectById(serviceItemId);
        if (item == null) {
            throw new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND);
        }
        return toRef(item);
    }

    private ServiceItemRef toRef(ServiceItemEntity item) {
        return new ServiceItemRef(item.getId(), item.getItemNo(), item.getItemName(),
                item.getDurationMinutes() == null ? 0 : item.getDurationMinutes(), item.getStatus());
    }

    /**
     * 生成下单快照（跨模块契约）。
     *
     * <p>只接受已发布项目：订单不能基于已下架商品创建。
     * 已下架项目会返回 {@code SERVICE_ITEM_NOT_FOUND}——对下单方而言，
     * "查不到"和"下架了"应当走同一分支，否则前端要处理两种"不能买"。</p>
     *
     * @param itemNo 服务项目编号
     * @return 服务项目快照（含标准检查项）
     * @throws BusinessException 不存在或不可售
     */
    @Transactional(readOnly = true)
    public ServiceItemSnapshot requireSnapshot(String itemNo) {
        ServiceItemEntity item = itemMapper.selectPublishedByItemNo(itemNo);
        if (item == null) {
            throw new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND);
        }
        return new ServiceItemSnapshot(item.getId(), item.getItemNo(), item.getItemName(),
                item.getVersion(), item.getServiceUnit(), item.getPricingMode(),
                item.getDurationMinutes(), item.getBasePrice(), item.getServiceStandardSummary(),
                standardSnapshots(item.getId()), LocalDateTime.now());
    }

    /**
     * 查询标准检查项快照（跨模块契约 {@code ServiceStandardSnapshotService} 的落地）。
     *
     * @param serviceItemId 服务项目 ID
     * @return 检查项快照列表
     */
    @Transactional(readOnly = true)
    public List<ServiceItemSnapshot.StandardSnapshot> standardSnapshots(long serviceItemId) {
        List<ServiceStandardItemEntity> entities = standardMapper.selectActiveByItemId(serviceItemId);
        List<ServiceItemSnapshot.StandardSnapshot> snapshots = new ArrayList<>(entities.size());
        for (ServiceStandardItemEntity entity : entities) {
            snapshots.add(new ServiceItemSnapshot.StandardSnapshot(entity.getStandardGroup(),
                    entity.getItemName(), entity.getDescription(),
                    Boolean.TRUE.equals(entity.getRequired()),
                    Boolean.TRUE.equals(entity.getEvidenceRequired()),
                    entity.getSortOrder() == null ? 0 : entity.getSortOrder()));
        }
        return snapshots;
    }

    /**
     * 组装详情视图。
     *
     * @param item 服务项目
     * @return 详情
     */
    @Transactional(readOnly = true)
    public ServiceItemDetailView assembleDetail(ServiceItemEntity item) {
        ServiceCategoryEntity category = categoryMapper.selectById(item.getCategoryId());
        List<ServiceItemOptionEntity> options = optionMapper.selectActiveByItemId(item.getId());
        List<OptionView> optionViews = buildOptionViews(options);
        List<TierView> tierViews = buildTierViews(item.getId());
        List<StandardItemView> standardViews = buildStandardViews(item.getId());

        return new ServiceItemDetailView(item.getItemNo(), item.getItemName(),
                item.getShortDescription(), item.getDetailDescription(), item.getCoverUrl(),
                category == null ? null : category.getCategoryNo(),
                category == null ? null : category.getCategoryName(),
                item.getServiceUnit(), item.getPricingMode(), item.getBasePrice(),
                item.getDurationMinutes(), item.getMinQuantity(), item.getMaxQuantity(),
                item.getServiceScope(), item.getServiceStandardSummary(),
                item.getVersion(), item.getStatus(), optionViews, tierViews, standardViews);
    }

    /**
     * 组装规格组视图（含规格值）。
     *
     * @param options 规格组实体
     * @return 规格组视图
     */
    @Transactional(readOnly = true)
    public List<OptionView> buildOptionViews(List<ServiceItemOptionEntity> options) {
        if (options.isEmpty()) {
            return List.of();
        }
        List<Long> optionIds = options.stream().map(ServiceItemOptionEntity::getId).toList();
        Map<Long, List<OptionView.OptionValueView>> valuesByOption = new HashMap<>();
        for (ServiceItemOptionValueEntity value : optionValueMapper.selectActiveByOptionIds(optionIds)) {
            valuesByOption.computeIfAbsent(value.getOptionId(), key -> new ArrayList<>())
                    .add(new OptionView.OptionValueView(value.getValueCode(), value.getValueName(),
                            value.getNumericValue(), value.getExtraPrice(),
                            value.getDurationDelta() == null ? 0 : value.getDurationDelta()));
        }
        List<OptionView> views = new ArrayList<>(options.size());
        for (ServiceItemOptionEntity option : options) {
            views.add(new OptionView(option.getOptionCode(), option.getOptionName(),
                    option.getSelectionType(), Boolean.TRUE.equals(option.getRequired()),
                    option.getMinSelect() == null ? 0 : option.getMinSelect(),
                    option.getMaxSelect() == null ? 1 : option.getMaxSelect(),
                    valuesByOption.getOrDefault(option.getId(), List.of())));
        }
        return views;
    }

    /**
     * 组装阶梯视图。
     *
     * @param serviceItemId 服务项目 ID
     * @return 阶梯视图
     */
    @Transactional(readOnly = true)
    public List<TierView> buildTierViews(long serviceItemId) {
        List<ServicePriceTierEntity> tiers = tierMapper.selectActiveByItemId(serviceItemId);
        List<TierView> views = new ArrayList<>(tiers.size());
        for (ServicePriceTierEntity tier : tiers) {
            views.add(new TierView(tier.getTierType(), tier.getMinValue(), tier.getMaxValue(),
                    tier.getFixedPrice(), tier.getUnitPrice()));
        }
        return views;
    }

    private List<StandardItemView> buildStandardViews(long serviceItemId) {
        List<ServiceStandardItemEntity> entities = standardMapper.selectActiveByItemId(serviceItemId);
        List<StandardItemView> views = new ArrayList<>(entities.size());
        for (ServiceStandardItemEntity entity : entities) {
            views.add(new StandardItemView(entity.getStandardGroup(), entity.getItemName(),
                    entity.getDescription(), Boolean.TRUE.equals(entity.getRequired()),
                    Boolean.TRUE.equals(entity.getEvidenceRequired()),
                    entity.getSortOrder() == null ? 0 : entity.getSortOrder()));
        }
        return views;
    }

    private ServiceItemSummaryView toSummary(ServiceItemEntity item, ServiceCategoryEntity category) {
        return new ServiceItemSummaryView(item.getItemNo(), item.getItemName(),
                item.getShortDescription(), item.getCoverUrl(),
                category == null ? null : category.getCategoryNo(),
                category == null ? null : category.getCategoryName(),
                item.getServiceUnit(), item.getPricingMode(), item.getBasePrice(),
                item.getDurationMinutes(), item.getVersion(), item.getStatus());
    }

    /**
     * 判断计价模式是否依赖阶梯，供报价与发布校验复用。
     *
     * @param item 服务项目
     * @return 是否需要阶梯
     */
    public boolean requiresTiers(ServiceItemEntity item) {
        return PricingMode.of(item.getPricingMode()).requiresTiers();
    }
}
