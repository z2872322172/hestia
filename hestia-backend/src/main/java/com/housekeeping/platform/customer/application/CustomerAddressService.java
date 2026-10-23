package com.housekeeping.platform.customer.application;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.customer.application.command.AddressCommand;
import com.housekeeping.platform.customer.application.contract.AddressSnapshotQueryService;
import com.housekeeping.platform.customer.application.view.AddressRegionView;
import com.housekeeping.platform.customer.application.view.AddressSnapshot;
import com.housekeeping.platform.customer.application.view.AddressView;
import com.housekeeping.platform.customer.domain.AddressAreaMatchStatus;
import com.housekeeping.platform.customer.domain.AddressLabel;
import com.housekeeping.platform.customer.domain.AddressStatus;
import com.housekeeping.platform.customer.persistence.entity.CustomerAddressEntity;
import com.housekeeping.platform.customer.persistence.entity.CustomerProfileEntity;
import com.housekeeping.platform.customer.persistence.mapper.CustomerAddressMapper;
import com.housekeeping.platform.customer.persistence.mapper.CustomerProfileMapper;
import com.housekeeping.platform.identity.domain.PhoneNumber;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.location.application.RegionDictionaryService;
import com.housekeeping.platform.location.application.ServiceAreaMatchService;
import com.housekeeping.platform.location.domain.AreaMatchStatus;
import com.housekeeping.platform.location.domain.ServiceAreaMatchQuery;
import com.housekeeping.platform.location.domain.ServiceAreaMatchResult;
import com.housekeeping.platform.location.persistence.entity.AdministrativeRegionEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 客户地址服务。
 *
 * <p>关键规则（docs/modules/02 第 7 节）：</p>
 * <ul>
 *   <li>地址归属通过 {@code customer_id} 校验，越权一律返回"地址不存在"，
 *       不能通过错误码差异探测他人地址编号；</li>
 *   <li>首个有效地址自动成为默认地址；</li>
 *   <li>删除默认地址时，自动把最近使用的有效地址提升为默认；</li>
 *   <li>行政区或详细地址变化后必须重新匹配服务区域；</li>
 *   <li>只有 ACTIVE + MATCHED 的地址能生成下单快照。</li>
 * </ul>
 */
@Service
@RequiresPersistence
public class CustomerAddressService implements AddressSnapshotQueryService {

    private static final String ADDRESS_NO_SEQ_KEY = "ADDRESS";

    private static final String ADDRESS_NO_PREFIX = "ADDR";

    private final CustomerAddressMapper addressMapper;

    private final CustomerProfileMapper profileMapper;

    private final CustomerProfileService profileService;

    private final ServiceAreaMatchService serviceAreaMatchService;

    private final RegionDictionaryService regionDictionaryService;

    private final BusinessNoGenerator businessNoGenerator;

    /** 每个客户的有效地址上限，docs/modules/02 第 7.2 节默认 20。 */
    private final int addressLimit;

    public CustomerAddressService(CustomerAddressMapper addressMapper,
                                  CustomerProfileMapper profileMapper,
                                  CustomerProfileService profileService,
                                  ServiceAreaMatchService serviceAreaMatchService,
                                  RegionDictionaryService regionDictionaryService,
                                  BusinessNoGenerator businessNoGenerator,
                                  @Value("${app.customer.address-limit:20}") int addressLimit) {
        this.addressMapper = addressMapper;
        this.profileMapper = profileMapper;
        this.profileService = profileService;
        this.serviceAreaMatchService = serviceAreaMatchService;
        this.regionDictionaryService = regionDictionaryService;
        this.businessNoGenerator = businessNoGenerator;
        this.addressLimit = addressLimit;
    }

    /**
     * 查询当前客户的有效地址列表。
     *
     * @param accountId 当前账号 ID
     * @return 地址列表
     */
    @Transactional(readOnly = true)
    public List<AddressView> list(long accountId) {
        CustomerProfileEntity profile = profileService.requireByAccountId(accountId);
        return addressMapper.selectActiveByCustomerId(profile.getId()).stream().map(this::toView).toList();
    }

    /**
     * 查询单个地址详情。
     *
     * @param accountId 当前账号 ID
     * @param addressNo 地址编号
     * @return 地址详情
     */
    @Transactional(readOnly = true)
    public AddressView detail(long accountId, String addressNo) {
        CustomerProfileEntity profile = profileService.requireByAccountId(accountId);
        return toView(requireOwned(profile.getId(), addressNo));
    }

    /**
     * 新增地址。
     *
     * @param accountId 当前账号 ID
     * @param command   地址命令
     * @return 新建的地址
     */
    @Transactional
    public AddressView create(long accountId, AddressCommand command) {
        CustomerProfileEntity profile = profileService.requireUsableByAccountId(accountId);
        // 客户级行锁：地址上限是"跨行约束"，不锁住客户行时并发请求可以一起突破上限
        profileMapper.lockById(profile.getId());
        long activeCount = addressMapper.countActiveByCustomerId(profile.getId());
        if (activeCount >= addressLimit) {
            throw new BusinessException(ErrorCode.ADDRESS_LIMIT_EXCEEDED,
                    "最多保存 " + addressLimit + " 个地址");
        }

        CustomerAddressEntity entity = new CustomerAddressEntity();
        entity.setAddressNo(businessNoGenerator.next(ADDRESS_NO_SEQ_KEY, ADDRESS_NO_PREFIX));
        entity.setCustomerId(profile.getId());
        entity.setStatus(AddressStatus.ACTIVE.name());
        applyCommand(entity, command, false);
        applyAreaMatch(entity, command);

        // 首个地址强制默认；否则按客户端请求，且必须先清掉旧默认（唯一索引不允许两个）
        boolean makeDefault = activeCount == 0 || Boolean.TRUE.equals(command.isDefault());
        if (makeDefault && activeCount > 0) {
            addressMapper.clearDefault(profile.getId());
        }
        entity.setIsDefault(makeDefault);
        addressMapper.insert(entity);
        return toView(entity);
    }

    /**
     * 修改地址。
     *
     * @param accountId 当前账号 ID
     * @param addressNo 地址编号
     * @param command   地址命令（含版本号）
     * @return 修改后的地址
     */
    @Transactional
    public AddressView update(long accountId, String addressNo, AddressCommand command) {
        CustomerProfileEntity profile = profileService.requireUsableByAccountId(accountId);
        CustomerAddressEntity entity = requireOwned(profile.getId(), addressNo);
        if (command.version() == null || !command.version().equals(entity.getVersion())) {
            throw new BusinessException(ErrorCode.ADDRESS_VERSION_CONFLICT);
        }

        boolean locationChanged = !Objects.equals(entity.getDistrictCode(), command.districtCode())
                || !Objects.equals(entity.getCommunityName(), command.communityName())
                || !Objects.equals(entity.getDetailAddress(), command.detailAddress())
                || !Objects.equals(entity.getStreetCode(), command.streetCode());
        applyCommand(entity, command, true);
        if (locationChanged) {
            // 位置变了就重新匹配；不重新匹配会让地址继续挂着旧服务区域，下单时才发现错
            applyAreaMatch(entity, command);
        }
        entity.setVersion(command.version());
        if (addressMapper.updateById(entity) == 0) {
            throw new BusinessException(ErrorCode.ADDRESS_VERSION_CONFLICT);
        }
        return toView(requireOwned(profile.getId(), addressNo));
    }

    /**
     * 删除地址（软删除）。
     *
     * @param accountId 当前账号 ID
     * @param addressNo 地址编号
     */
    @Transactional
    public void delete(long accountId, String addressNo) {
        CustomerProfileEntity profile = profileService.requireUsableByAccountId(accountId);
        CustomerAddressEntity entity = requireOwned(profile.getId(), addressNo);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            // 顺序不能反：先清默认，再提升新默认，最后软删除。
            // 反过来会在"提升新默认"时触发 uk_customer_active_default 唯一索引冲突。
            addressMapper.clearDefault(profile.getId());
            CustomerAddressEntity candidate = addressMapper.selectMostRecent(profile.getId(), entity.getId());
            if (candidate != null) {
                addressMapper.markDefault(candidate.getId(), profile.getId());
            }
        }
        if (addressMapper.softDelete(entity.getId(), profile.getId()) == 0) {
            throw new BusinessException(ErrorCode.ADDRESS_DELETED);
        }
    }

    /**
     * 设为默认地址。
     *
     * @param accountId 当前账号 ID
     * @param addressNo 地址编号
     * @return 设置后的地址
     */
    @Transactional
    public AddressView setDefault(long accountId, String addressNo) {
        CustomerProfileEntity profile = profileService.requireUsableByAccountId(accountId);
        CustomerAddressEntity entity = requireOwned(profile.getId(), addressNo);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            return toView(entity);
        }
        addressMapper.clearDefault(profile.getId());
        try {
            if (addressMapper.markDefault(entity.getId(), profile.getId()) == 0) {
                throw new BusinessException(ErrorCode.ADDRESS_DELETED);
            }
        } catch (DuplicateKeyException exception) {
            // 并发设置默认地址：唯一索引只允许一个成功，后到者明确失败而不是静默覆盖
            throw new BusinessException(ErrorCode.ADDRESS_DEFAULT_CONFLICT);
        }
        return toView(requireOwned(profile.getId(), addressNo));
    }

    /**
     * 重新检查地址的服务范围。
     *
     * <p>不是锁单操作：真正的时段与人员可用性由排班、订单模块校验。</p>
     *
     * @param accountId 当前账号 ID
     * @param addressNo 地址编号
     * @return 更新后的地址
     */
    @Transactional
    public AddressView checkAvailability(long accountId, String addressNo) {
        CustomerProfileEntity profile = profileService.requireUsableByAccountId(accountId);
        CustomerAddressEntity entity = requireOwned(profile.getId(), addressNo);
        AddressCommand command = new AddressCommand(entity.getLabel(), entity.getContactName(),
                entity.getContactCountryCode(), entity.getContactPhone(), entity.getProvinceCode(),
                entity.getCityCode(), entity.getDistrictCode(), entity.getStreetCode(),
                entity.getCommunityName(), entity.getBuildingNo(), entity.getUnitNo(),
                entity.getFloorNo(), entity.getRoomNo(), entity.getDetailAddress(),
                entity.getLongitude(), entity.getLatitude(), entity.getAccessNote(),
                entity.getIsDefault(), entity.getVersion());
        applyAreaMatch(entity, command);
        addressMapper.updateAreaMatch(entity.getId(), entity.getServiceAreaId(), entity.getAreaMatchStatus());
        return toView(requireOwned(profile.getId(), addressNo));
    }

    /**
     * 生成下单地址快照（{@code AddressSnapshotQueryService} 契约实现）。
     *
     * <p>只允许 {@code ACTIVE + MATCHED} 的地址生成快照；订单模块拿到快照后
     * 不再依赖地址簿，后续改地址不会影响历史订单。</p>
     *
     * @param accountId 当前账号 ID
     * @param addressNo 地址编号
     * @return 快照
     */
    @Override
    @Transactional
    public AddressSnapshot requireSnapshot(long accountId, String addressNo) {
        CustomerProfileEntity profile = profileService.requireUsableByAccountId(accountId);
        CustomerAddressEntity entity = requireOwned(profile.getId(), addressNo);
        if (!AddressAreaMatchStatus.MATCHED.name().equals(entity.getAreaMatchStatus())
                || entity.getServiceAreaId() == null) {
            throw new BusinessException(AddressAreaMatchStatus.OUT_OF_RANGE.name().equals(entity.getAreaMatchStatus())
                    ? ErrorCode.ADDRESS_AREA_OUT_OF_RANGE : ErrorCode.ADDRESS_AREA_NOT_MATCHED);
        }
        // 标记最近使用：删除默认地址时用它来决定提升谁
        addressMapper.touchLastUsed(entity.getId());
        return new AddressSnapshot(entity.getId(), entity.getAddressNo(), entity.getVersion(),
                entity.getContactName(), entity.getContactCountryCode(), entity.getContactPhone(),
                entity.getProvinceCode(), entity.getProvinceName(), entity.getCityCode(), entity.getCityName(),
                entity.getDistrictCode(), entity.getDistrictName(), entity.getStreetCode(), entity.getStreetName(),
                entity.getServiceAreaId(), entity.getCommunityName(), entity.getBuildingNo(), entity.getUnitNo(),
                entity.getFloorNo(), entity.getRoomNo(), entity.getDetailAddress(),
                entity.getLongitude(), entity.getLatitude(), entity.getAccessNote(), LocalDateTime.now());
    }

    /**
     * 取当前客户名下的地址，越权与不存在返回同一个错误。
     *
     * @param customerId 客户档案 ID
     * @param addressNo  地址编号
     * @return 地址实体
     */
    private CustomerAddressEntity requireOwned(long customerId, String addressNo) {
        CustomerAddressEntity entity = addressMapper.selectByAddressNo(addressNo);
        if (entity == null || !Objects.equals(entity.getCustomerId(), customerId)
                || !AddressStatus.ACTIVE.name().equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 把命令写入实体，并补齐服务端权威字段（区县/城市名称、归一化电话）。
     */
    private void applyCommand(CustomerAddressEntity entity, AddressCommand command, boolean updating) {
        PhoneNumber phone;
        try {
            phone = PhoneNumber.parse(command.contactCountryCode(), command.contactPhone());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "联系人电话格式不正确");
        }
        entity.setLabel(AddressLabel.ofOrDefault(command.label()).name());
        entity.setContactName(command.contactName());
        entity.setContactCountryCode(phone.countryCode());
        entity.setContactPhone(phone.number());

        AdministrativeRegionEntity district = regionDictionaryService.requireByCode(command.districtCode());
        entity.setDistrictCode(district.getRegionCode());
        entity.setDistrictName(district.getRegionName());
        if (district.getParentId() != null) {
            AdministrativeRegionEntity city = regionDictionaryService.requireById(district.getParentId());
            entity.setCityCode(city.getRegionCode());
            entity.setCityName(city.getRegionName());
            if (city.getParentId() != null) {
                AdministrativeRegionEntity province = regionDictionaryService.requireById(city.getParentId());
                entity.setProvinceCode(province.getRegionCode());
                entity.setProvinceName(province.getRegionName());
            }
        }
        if (command.streetCode() != null && !command.streetCode().isBlank()) {
            AdministrativeRegionEntity street = regionDictionaryService.requireByCode(command.streetCode());
            entity.setStreetCode(street.getRegionCode());
            entity.setStreetName(street.getRegionName());
        } else if (!updating) {
            entity.setStreetCode(null);
            entity.setStreetName(null);
        }
        entity.setCommunityName(command.communityName());
        entity.setBuildingNo(command.buildingNo());
        entity.setUnitNo(command.unitNo());
        entity.setFloorNo(command.floorNo());
        entity.setRoomNo(command.roomNo());
        entity.setDetailAddress(command.detailAddress());
        entity.setLongitude(command.longitude());
        entity.setLatitude(command.latitude());
        entity.setAccessNote(command.accessNote());
    }

    /**
     * 调用位置模块匹配服务区域并回写状态。
     *
     * <p>映射关系（docs/modules/03 第 16.2 节）：
     * MATCHED → MATCHED；OUT_OF_RANGE → OUT_OF_RANGE；
     * AMBIGUOUS / REGION_INVALID 必须抛错，不能伪装成"不在服务范围"。</p>
     */
    private void applyAreaMatch(CustomerAddressEntity entity, AddressCommand command) {
        // 城市编码以 applyCommand 从行政区字典推导出的值为准，不信任客户端传入的 cityCode
        String cityCode = entity.getCityCode();
        if (cityCode == null || cityCode.isBlank()) {
            cityCode = regionDictionaryService.cityCodeOfDistrict(entity.getDistrictCode());
        }
        ServiceAreaMatchResult result = serviceAreaMatchService.match(new ServiceAreaMatchQuery(
                cityCode, entity.getDistrictCode(), entity.getStreetCode(),
                command.longitude() == null ? null : command.longitude().doubleValue(),
                command.latitude() == null ? null : command.latitude().doubleValue()));

        switch (result.matchStatus()) {
            case MATCHED -> {
                entity.setServiceAreaId(result.serviceAreaId());
                entity.setAreaMatchStatus(AddressAreaMatchStatus.MATCHED.name());
            }
            case OUT_OF_RANGE -> {
                entity.setServiceAreaId(null);
                entity.setAreaMatchStatus(AddressAreaMatchStatus.OUT_OF_RANGE.name());
            }
            case AMBIGUOUS -> throw new BusinessException(ErrorCode.SERVICE_AREA_AMBIGUOUS);
            case REGION_INVALID -> throw new BusinessException(ErrorCode.REGION_NOT_FOUND, result.message());
        }
        entity.setAreaMatchedAt(LocalDateTime.now());
    }

    private AddressView toView(CustomerAddressEntity entity) {
        return new AddressView(entity.getAddressNo(), entity.getLabel(), entity.getContactName(),
                entity.getContactPhone(),
                new AddressRegionView(entity.getCityName(), entity.getDistrictName()),
                entity.getCommunityName(), entity.getBuildingNo(), entity.getUnitNo(),
                entity.getFloorNo(), entity.getRoomNo(), entity.getDetailAddress(),
                entity.getAccessNote(), entity.getIsDefault(), entity.getAreaMatchStatus(),
                entity.getServiceAreaId(), entity.getVersion());
    }
}
