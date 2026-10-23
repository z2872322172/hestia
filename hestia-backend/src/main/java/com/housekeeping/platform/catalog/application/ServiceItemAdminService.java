package com.housekeeping.platform.catalog.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housekeeping.platform.catalog.application.command.ServiceItemCommand;
import com.housekeeping.platform.catalog.application.command.StandardCommand;
import com.housekeeping.platform.catalog.application.view.ServiceItemDetailView;
import com.housekeeping.platform.catalog.application.view.ServiceItemSummaryView;
import com.housekeeping.platform.catalog.application.view.StandardItemView;
import com.housekeeping.platform.catalog.domain.PriceCalculator;
import com.housekeeping.platform.catalog.domain.PricedTier;
import com.housekeeping.platform.catalog.domain.PricingMode;
import com.housekeeping.platform.catalog.domain.ServiceCategoryStatus;
import com.housekeeping.platform.catalog.domain.ServiceItemStatus;
import com.housekeeping.platform.catalog.domain.TierType;
import com.housekeeping.platform.catalog.persistence.entity.ServiceCategoryEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemOptionEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemOptionValueEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServicePriceTierEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServiceStandardItemEntity;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceItemMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceItemOptionMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceItemOptionValueMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServicePriceTierMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceStandardItemMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 服务项目运营维护。
 *
 * <p>版本号语义：每次成功修改 {@code version + 1}，订单快照记录当时的版本。
 * 这里用显式的 {@code WHERE id = ? AND version = ?} 条件更新，而不是依赖
 * MyBatis-Plus 的 {@code @Version} 拦截器——因为需要把可空字段（如
 * {@code max_quantity}）显式写成 {@code NULL}，而拦截器只对实体更新生效，
 * 实体更新会跳过 {@code null} 字段，"取消数量上限"就永远改不掉。</p>
 */
@Service
@RequiresPersistence
public class ServiceItemAdminService {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceItemAdminService.class);

    private static final String ITEM_NO_SEQ_KEY = "SERVICE_ITEM";

    private static final String ITEM_NO_PREFIX = "SVC";

    private final ServiceItemMapper itemMapper;

    private final ServiceItemOptionMapper optionMapper;

    private final ServiceItemOptionValueMapper optionValueMapper;

    private final ServicePriceTierMapper tierMapper;

    private final ServiceStandardItemMapper standardMapper;

    private final ServiceCategoryService categoryService;

    private final ServiceCatalogQueryService catalogQueryService;

    private final BusinessNoGenerator businessNoGenerator;

    public ServiceItemAdminService(ServiceItemMapper itemMapper,
                                   ServiceItemOptionMapper optionMapper,
                                   ServiceItemOptionValueMapper optionValueMapper,
                                   ServicePriceTierMapper tierMapper,
                                   ServiceStandardItemMapper standardMapper,
                                   ServiceCategoryService categoryService,
                                   ServiceCatalogQueryService catalogQueryService,
                                   BusinessNoGenerator businessNoGenerator) {
        this.itemMapper = itemMapper;
        this.optionMapper = optionMapper;
        this.optionValueMapper = optionValueMapper;
        this.tierMapper = tierMapper;
        this.standardMapper = standardMapper;
        this.categoryService = categoryService;
        this.catalogQueryService = catalogQueryService;
        this.businessNoGenerator = businessNoGenerator;
    }

    /**
     * 创建草稿服务项目。
     *
     * @param command 创建命令
     * @return 详情
     * @throws BusinessException 分类不可用或参数非法
     */
    @Transactional
    public ServiceItemDetailView create(ServiceItemCommand command) {
        validate(command);
        ServiceCategoryEntity category = requireUsableCategory(command.categoryNo());

        ServiceItemEntity entity = new ServiceItemEntity();
        entity.setItemNo(businessNoGenerator.next(ITEM_NO_SEQ_KEY, ITEM_NO_PREFIX));
        applyCommand(entity, command, category.getId());
        entity.setStatus(ServiceItemStatus.DRAFT.name());
        itemMapper.insert(entity);

        replaceOptions(entity.getId(), command.options());
        replaceTiers(entity.getId(), command.tiers());
        LOG.info("创建服务项目 itemNo={} category={}", entity.getItemNo(), category.getCategoryNo());
        return catalogQueryService.adminDetail(entity.getItemNo());
    }

    /**
     * 修改服务项目（乐观锁）。
     *
     * @param itemNo          服务项目编号
     * @param expectedVersion 客户端持有的版本号
     * @param command         修改命令
     * @return 详情
     * @throws BusinessException 不存在、版本冲突或参数非法
     */
    @Transactional
    public ServiceItemDetailView update(String itemNo, long expectedVersion, ServiceItemCommand command) {
        validate(command);
        ServiceItemEntity item = catalogQueryService.requireItem(itemNo);
        if (item.getVersion() == null || item.getVersion() != expectedVersion) {
            throw new BusinessException(ErrorCode.SERVICE_ITEM_VERSION_CONFLICT);
        }
        ServiceCategoryEntity category = requireUsableCategory(command.categoryNo());

        LambdaUpdateWrapper<ServiceItemEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ServiceItemEntity::getId, item.getId())
                .eq(ServiceItemEntity::getVersion, expectedVersion)
                .set(ServiceItemEntity::getCategoryId, category.getId())
                .set(ServiceItemEntity::getItemName, command.itemName())
                .set(ServiceItemEntity::getShortDescription, command.shortDescription())
                .set(ServiceItemEntity::getDetailDescription, command.detailDescription())
                .set(ServiceItemEntity::getCoverUrl, command.coverUrl())
                .set(ServiceItemEntity::getServiceUnit, command.serviceUnit().name())
                .set(ServiceItemEntity::getPricingMode, command.pricingMode().name())
                .set(ServiceItemEntity::getBasePrice, command.basePrice())
                .set(ServiceItemEntity::getDurationMinutes, command.durationMinutes())
                .set(ServiceItemEntity::getMinQuantity, command.minQuantity())
                .set(ServiceItemEntity::getMaxQuantity, command.maxQuantity())
                .set(ServiceItemEntity::getServiceScope, command.serviceScope())
                .set(ServiceItemEntity::getServiceStandardSummary, command.serviceStandardSummary())
                .set(ServiceItemEntity::getVersion, expectedVersion + 1);

        if (itemMapper.update(null, wrapper) == 0) {
            throw new BusinessException(ErrorCode.SERVICE_ITEM_VERSION_CONFLICT);
        }

        replaceOptions(item.getId(), command.options());
        replaceTiers(item.getId(), command.tiers());
        LOG.info("修改服务项目 itemNo={} version {} -> {}", itemNo, expectedVersion, expectedVersion + 1);
        return catalogQueryService.adminDetail(itemNo);
    }

    /**
     * 发布服务项目。
     *
     * <p>发布前要过四道校验：分类仍可用、至少一条标准检查项、阶梯价项目必须有档位、
     * 档位区间不重叠。少任何一道，都会产出一个"能查到但算不出价"或
     * "履约时没有检查项"的在售商品。</p>
     *
     * @param itemNo 服务项目编号
     * @return 详情
     * @throws BusinessException 状态不允许或配置不完整
     */
    @Transactional
    public ServiceItemDetailView publish(String itemNo) {
        ServiceItemEntity item = catalogQueryService.requireItem(itemNo);
        ServiceItemStatus current = ServiceItemStatus.valueOf(item.getStatus());
        if (!current.canTransitionTo(ServiceItemStatus.PUBLISHED)) {
            throw new BusinessException(ErrorCode.SERVICE_ITEM_STATUS_CONFLICT);
        }

        ServiceCategoryEntity category = categoryService.requireById(item.getCategoryId());
        if (!ServiceCategoryStatus.ACTIVE.name().equals(category.getStatus())) {
            throw new BusinessException(ErrorCode.SERVICE_CATEGORY_DISABLED);
        }
        if (standardMapper.countActiveByItemId(item.getId()) == 0) {
            throw new BusinessException(ErrorCode.SERVICE_STANDARD_REQUIRED);
        }
        assertTiersReady(item);

        if (itemMapper.publish(item.getId()) == 0) {
            // 并发发布：另一个人先改成功了
            throw new BusinessException(ErrorCode.SERVICE_ITEM_STATUS_CONFLICT);
        }
        LOG.info("发布服务项目 itemNo={}", itemNo);
        return catalogQueryService.adminDetail(itemNo);
    }

    /**
     * 下架服务项目。
     *
     * @param itemNo 服务项目编号
     * @return 详情
     * @throws BusinessException 状态不允许
     */
    @Transactional
    public ServiceItemDetailView offline(String itemNo) {
        ServiceItemEntity item = catalogQueryService.requireItem(itemNo);
        ServiceItemStatus current = ServiceItemStatus.valueOf(item.getStatus());
        if (!current.canTransitionTo(ServiceItemStatus.OFFLINE)) {
            throw new BusinessException(ErrorCode.SERVICE_ITEM_STATUS_CONFLICT);
        }
        if (itemMapper.offline(item.getId()) == 0) {
            throw new BusinessException(ErrorCode.SERVICE_ITEM_STATUS_CONFLICT);
        }
        LOG.info("下架服务项目 itemNo={}", itemNo);
        return catalogQueryService.adminDetail(itemNo);
    }

    /**
     * 整体替换服务标准。
     *
     * @param itemNo  服务项目编号
     * @param command 标准命令
     * @return 替换后的检查项
     * @throws BusinessException 服务项目不存在或检查项名称为空
     */
    @Transactional
    public List<StandardItemView> replaceStandards(String itemNo, StandardCommand command) {
        ServiceItemEntity item = catalogQueryService.requireItem(itemNo);
        standardMapper.deleteByItemId(item.getId());

        List<StandardCommand.StandardItemCommand> items = command == null || command.items() == null
                ? List.of() : command.items();
        int autoOrder = 0;
        for (StandardCommand.StandardItemCommand cmd : items) {
            if (cmd.itemName() == null || cmd.itemName().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            ServiceStandardItemEntity entity = new ServiceStandardItemEntity();
            entity.setServiceItemId(item.getId());
            entity.setStandardGroup(cmd.standardGroup());
            entity.setItemName(cmd.itemName());
            entity.setDescription(cmd.description());
            entity.setRequired(cmd.required());
            entity.setEvidenceRequired(cmd.evidenceRequired());
            entity.setSortOrder(cmd.sortOrder() == null ? ++autoOrder : cmd.sortOrder());
            entity.setStatus("ACTIVE");
            standardMapper.insert(entity);
        }
        LOG.info("替换服务标准 itemNo={} items={}", itemNo, items.size());
        return catalogQueryService.assembleDetail(catalogQueryService.requireItem(itemNo)).standards();
    }

    /**
     * 运营后台分页查询服务项目。
     *
     * @param page    页码
     * @param size    每页条数
     * @param status  状态过滤，为空表示全部
     * @param keyword 关键字，匹配编号或名称
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ServiceItemSummaryView> page(int page, int size, String status, String keyword) {
        LambdaQueryWrapper<ServiceItemEntity> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(ServiceItemEntity::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(condition -> condition
                    .like(ServiceItemEntity::getItemNo, trimmed)
                    .or()
                    .like(ServiceItemEntity::getItemName, trimmed));
        }
        wrapper.orderByDesc(ServiceItemEntity::getId);
        Page<ServiceItemEntity> result = itemMapper.selectPage(new Page<>(page, size), wrapper);

        List<ServiceItemSummaryView> records = new ArrayList<>(result.getRecords().size());
        for (ServiceItemEntity entity : result.getRecords()) {
            records.add(toSummary(entity));
        }
        return new PageResult<>(records, page, size, result.getTotal());
    }

    private ServiceItemSummaryView toSummary(ServiceItemEntity entity) {
        ServiceCategoryEntity category = categoryService.requireById(entity.getCategoryId());
        return new ServiceItemSummaryView(entity.getItemNo(), entity.getItemName(),
                entity.getShortDescription(), entity.getCoverUrl(), category.getCategoryNo(),
                category.getCategoryName(), entity.getServiceUnit(), entity.getPricingMode(),
                entity.getBasePrice(), entity.getDurationMinutes(),
                entity.getVersion() == null ? 0L : entity.getVersion(), entity.getStatus());
    }

    private ServiceCategoryEntity requireUsableCategory(String categoryNo) {
        ServiceCategoryEntity category = categoryService.requireByNo(categoryNo);
        if (!ServiceCategoryStatus.ACTIVE.name().equals(category.getStatus())) {
            throw new BusinessException(ErrorCode.SERVICE_CATEGORY_DISABLED);
        }
        return category;
    }

    private void assertTiersReady(ServiceItemEntity item) {
        PricingMode mode = PricingMode.of(item.getPricingMode());
        if (!mode.requiresTiers()) {
            return;
        }
        TierType tierType = TierType.of(mode);
        List<ServicePriceTierEntity> tiers = tierMapper.selectActiveByItemAndType(item.getId(), tierType.name());
        if (tiers.isEmpty()) {
            throw new BusinessException(ErrorCode.SERVICE_PRICE_TIER_NOT_FOUND);
        }
        List<PricedTier> pricedTiers = new ArrayList<>(tiers.size());
        for (ServicePriceTierEntity tier : tiers) {
            pricedTiers.add(new PricedTier(tier.getMinValue(), tier.getMaxValue(),
                    tier.getFixedPrice(), tier.getUnitPrice()));
        }
        PriceCalculator.assertNoOverlap(pricedTiers);
    }

    private void applyCommand(ServiceItemEntity entity, ServiceItemCommand command, Long categoryId) {
        entity.setCategoryId(categoryId);
        entity.setItemName(command.itemName());
        entity.setShortDescription(command.shortDescription());
        entity.setDetailDescription(command.detailDescription());
        entity.setCoverUrl(command.coverUrl());
        entity.setServiceUnit(command.serviceUnit().name());
        entity.setPricingMode(command.pricingMode().name());
        entity.setBasePrice(command.basePrice());
        entity.setDurationMinutes(command.durationMinutes());
        entity.setMinQuantity(command.minQuantity());
        entity.setMaxQuantity(command.maxQuantity());
        entity.setServiceScope(command.serviceScope());
        entity.setServiceStandardSummary(command.serviceStandardSummary());
    }

    private void replaceOptions(Long itemId, List<ServiceItemCommand.OptionCommand> options) {
        List<ServiceItemOptionEntity> existing = optionMapper.selectAllByItemId(itemId);
        if (!existing.isEmpty()) {
            List<Long> optionIds = existing.stream().map(ServiceItemOptionEntity::getId).toList();
            optionValueMapper.deleteByOptionIds(optionIds);
            optionMapper.deleteByItemId(itemId);
        }
        if (options == null || options.isEmpty()) {
            return;
        }
        int optionOrder = 0;
        for (ServiceItemCommand.OptionCommand cmd : options) {
            ServiceItemOptionEntity option = new ServiceItemOptionEntity();
            option.setServiceItemId(itemId);
            option.setOptionCode(cmd.optionCode());
            option.setOptionName(cmd.optionName());
            option.setSelectionType(cmd.selectionType().name());
            option.setRequired(cmd.required());
            option.setMinSelect(cmd.minSelect() == null ? 0 : cmd.minSelect());
            option.setMaxSelect(cmd.maxSelect() == null ? 1 : cmd.maxSelect());
            option.setSortOrder(cmd.sortOrder() == null ? ++optionOrder : cmd.sortOrder());
            option.setStatus("ACTIVE");
            optionMapper.insert(option);

            int valueOrder = 0;
            for (ServiceItemCommand.OptionValueCommand valueCmd : cmd.values()) {
                ServiceItemOptionValueEntity value = new ServiceItemOptionValueEntity();
                value.setOptionId(option.getId());
                value.setValueCode(valueCmd.valueCode());
                value.setValueName(valueCmd.valueName());
                value.setNumericValue(valueCmd.numericValue());
                value.setExtraPrice(valueCmd.extraPrice() == null ? BigDecimal.ZERO : valueCmd.extraPrice());
                value.setDurationDelta(valueCmd.durationDelta() == null ? 0 : valueCmd.durationDelta());
                value.setSortOrder(valueCmd.sortOrder() == null ? ++valueOrder : valueCmd.sortOrder());
                value.setStatus("ACTIVE");
                optionValueMapper.insert(value);
            }
        }
    }

    private void replaceTiers(Long itemId, List<ServiceItemCommand.TierCommand> tiers) {
        tierMapper.deleteByItemId(itemId);
        if (tiers == null || tiers.isEmpty()) {
            return;
        }
        int order = 0;
        for (ServiceItemCommand.TierCommand cmd : tiers) {
            ServicePriceTierEntity entity = new ServicePriceTierEntity();
            entity.setServiceItemId(itemId);
            entity.setTierType(cmd.tierType());
            entity.setMinValue(cmd.minValue());
            entity.setMaxValue(cmd.maxValue());
            entity.setFixedPrice(cmd.fixedPrice());
            entity.setUnitPrice(cmd.unitPrice());
            entity.setSortOrder(cmd.sortOrder() == null ? ++order : cmd.sortOrder());
            entity.setStatus("ACTIVE");
            tierMapper.insert(entity);
        }
    }

    /**
     * 参数校验。
     *
     * <p>校验写在服务层而不是只靠 Controller 的 Bean Validation：内部调用
     * （例如批次 6 的数据初始化脚本）不经过 Controller，只在 Controller 校验等于没校验。</p>
     */
    private void validate(ServiceItemCommand command) {
        if (command.itemName() == null || command.itemName().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (command.serviceUnit() == null || command.pricingMode() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (command.basePrice() == null || command.basePrice().signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (command.durationMinutes() == null || command.durationMinutes() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        BigDecimal minQuantity = command.minQuantity() == null ? BigDecimal.ONE : command.minQuantity();
        if (minQuantity.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (command.maxQuantity() != null && command.maxQuantity().compareTo(minQuantity) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        validateOptions(command.options());
        validateTiers(command.pricingMode(), command.tiers());
    }

    private void validateOptions(List<ServiceItemCommand.OptionCommand> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        Set<String> codes = new HashSet<>();
        for (ServiceItemCommand.OptionCommand option : options) {
            if (option.optionCode() == null || option.optionCode().isBlank()
                    || option.optionName() == null || option.optionName().isBlank()
                    || option.selectionType() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            if (!codes.add(option.optionCode())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            int maxSelect = option.maxSelect() == null ? 1 : option.maxSelect();
            if (maxSelect < 1) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            if (option.selectionType().isSingle() && maxSelect != 1) {
                // 单选组的上限只能是 1，否则"单选"这个词就没有意义了
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            if (option.values() == null || option.values().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            Set<String> valueCodes = new HashSet<>();
            for (ServiceItemCommand.OptionValueCommand value : option.values()) {
                if (value.valueCode() == null || value.valueCode().isBlank()
                        || value.valueName() == null || value.valueName().isBlank()) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
                if (!valueCodes.add(value.valueCode())) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
            }
        }
    }

    private void validateTiers(PricingMode mode, List<ServiceItemCommand.TierCommand> tiers) {
        if (!mode.requiresTiers()) {
            if (tiers != null && !tiers.isEmpty()) {
                // 固定价项目配了阶梯：要么是选错了计价模式，要么是残留配置，都该拦下来
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            return;
        }
        if (tiers == null || tiers.isEmpty()) {
            throw new BusinessException(ErrorCode.SERVICE_PRICE_TIER_NOT_FOUND);
        }
        String expectedType = TierType.of(mode).name();
        List<PricedTier> pricedTiers = new ArrayList<>(tiers.size());
        for (ServiceItemCommand.TierCommand tier : tiers) {
            if (!expectedType.equals(tier.tierType())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            if (tier.minValue() == null || tier.minValue().signum() < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            if (tier.maxValue() != null && tier.maxValue().compareTo(tier.minValue()) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            boolean hasFixed = tier.fixedPrice() != null && tier.fixedPrice().signum() >= 0;
            boolean hasUnit = tier.unitPrice() != null && tier.unitPrice().signum() >= 0;
            if (!hasFixed && !hasUnit) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            pricedTiers.add(new PricedTier(tier.minValue(), tier.maxValue(),
                    tier.fixedPrice(), tier.unitPrice()));
        }
        PriceCalculator.assertNoOverlap(pricedTiers);
    }
}
