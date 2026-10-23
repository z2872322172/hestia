package com.housekeeping.platform.customer.application;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.customer.application.command.AddressCommand;
import com.housekeeping.platform.customer.persistence.entity.CustomerAddressEntity;
import com.housekeeping.platform.customer.persistence.entity.CustomerProfileEntity;
import com.housekeeping.platform.customer.persistence.mapper.CustomerAddressMapper;
import com.housekeeping.platform.customer.persistence.mapper.CustomerProfileMapper;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.location.application.RegionDictionaryService;
import com.housekeeping.platform.location.application.ServiceAreaMatchService;
import com.housekeeping.platform.location.domain.AreaMatchStatus;
import com.housekeeping.platform.location.domain.CoverageLevel;
import com.housekeeping.platform.location.domain.ServiceAreaMatchResult;
import com.housekeeping.platform.location.persistence.entity.AdministrativeRegionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户地址服务单元测试。
 *
 * <p>重点覆盖 docs/modules/02 第 19.2 节的验收点：
 * 首个地址自动默认、默认地址唯一、删除默认后自动提升、跨客户不可见、地址上限。</p>
 */
@ExtendWith(MockitoExtension.class)
class CustomerAddressServiceTest {

    private static final long ACCOUNT_ID = 100L;

    private static final long CUSTOMER_ID = 200L;

    private static final int ADDRESS_LIMIT = 20;

    @Mock
    private CustomerAddressMapper addressMapper;

    @Mock
    private CustomerProfileMapper profileMapper;

    @Mock
    private CustomerProfileService profileService;

    @Mock
    private ServiceAreaMatchService serviceAreaMatchService;

    @Mock
    private RegionDictionaryService regionDictionaryService;

    @Mock
    private BusinessNoGenerator businessNoGenerator;

    private CustomerAddressService service;

    @BeforeEach
    void setUp() {
        service = new CustomerAddressService(addressMapper, profileMapper, profileService,
                serviceAreaMatchService, regionDictionaryService, businessNoGenerator, ADDRESS_LIMIT);
    }

    @Test
    @DisplayName("首个地址自动成为默认地址")
    void firstAddressBecomesDefault() {
        when(profileService.requireUsableByAccountId(ACCOUNT_ID)).thenReturn(profile());
        when(addressMapper.countActiveByCustomerId(CUSTOMER_ID)).thenReturn(0L);
        when(businessNoGenerator.next("ADDRESS", "ADDR")).thenReturn("ADDR202610230001");
        stubRegionDictionary();
        when(serviceAreaMatchService.match(any())).thenReturn(matchedResult());
        when(addressMapper.insert(any(CustomerAddressEntity.class))).thenReturn(1);

        var view = service.create(ACCOUNT_ID, command(null));

        assertThat(view.isDefault()).isTrue();
        assertThat(view.areaMatchStatus()).isEqualTo("MATCHED");
        verify(addressMapper, never()).clearDefault(anyLong());
    }

    @Test
    @DisplayName("地址数量达到上限时拒绝新增")
    void rejectsWhenAddressLimitReached() {
        when(profileService.requireUsableByAccountId(ACCOUNT_ID)).thenReturn(profile());
        when(addressMapper.countActiveByCustomerId(CUSTOMER_ID)).thenReturn((long) ADDRESS_LIMIT);

        assertThatThrownBy(() -> service.create(ACCOUNT_ID, command(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_LIMIT_EXCEEDED);

        verify(addressMapper, never()).insert(any(CustomerAddressEntity.class));
    }

    @Test
    @DisplayName("删除默认地址时先清默认、再提升最近使用的地址、最后软删除")
    void deleteDefaultPromotesMostRecent() {
        CustomerAddressEntity target = address(1L, true);
        CustomerAddressEntity candidate = address(2L, false);
        when(profileService.requireUsableByAccountId(ACCOUNT_ID)).thenReturn(profile());
        when(addressMapper.selectByAddressNo("ADDR202610230001")).thenReturn(target);
        when(addressMapper.selectMostRecent(CUSTOMER_ID, 1L)).thenReturn(candidate);
        when(addressMapper.markDefault(2L, CUSTOMER_ID)).thenReturn(1);
        when(addressMapper.softDelete(1L, CUSTOMER_ID)).thenReturn(1);

        service.delete(ACCOUNT_ID, "ADDR202610230001");

        // 顺序很关键：先提升新默认再软删除会触发默认地址唯一索引冲突
        InOrder order = inOrder(addressMapper);
        order.verify(addressMapper).clearDefault(CUSTOMER_ID);
        order.verify(addressMapper).markDefault(2L, CUSTOMER_ID);
        order.verify(addressMapper).softDelete(1L, CUSTOMER_ID);
    }

    @Test
    @DisplayName("没有其他有效地址时不提升默认")
    void deleteDefaultWithoutCandidate() {
        CustomerAddressEntity target = address(1L, true);
        when(profileService.requireUsableByAccountId(ACCOUNT_ID)).thenReturn(profile());
        when(addressMapper.selectByAddressNo("ADDR202610230001")).thenReturn(target);
        when(addressMapper.selectMostRecent(CUSTOMER_ID, 1L)).thenReturn(null);
        when(addressMapper.softDelete(1L, CUSTOMER_ID)).thenReturn(1);

        service.delete(ACCOUNT_ID, "ADDR202610230001");

        verify(addressMapper, never()).markDefault(anyLong(), anyLong());
    }

    @Test
    @DisplayName("并发设置默认地址时唯一索引冲突转成业务错误")
    void setDefaultConflictIsTranslated() {
        CustomerAddressEntity target = address(2L, false);
        when(profileService.requireUsableByAccountId(ACCOUNT_ID)).thenReturn(profile());
        when(addressMapper.selectByAddressNo("ADDR202610230001")).thenReturn(target);
        when(addressMapper.markDefault(2L, CUSTOMER_ID)).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> service.setDefault(ACCOUNT_ID, "ADDR202610230001"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_DEFAULT_CONFLICT);
    }

    @Test
    @DisplayName("访问他人地址返回地址不存在，不泄露存在性")
    void otherCustomerAddressIsInvisible() {
        CustomerAddressEntity others = address(9L, false);
        others.setCustomerId(999L);
        when(profileService.requireByAccountId(ACCOUNT_ID)).thenReturn(profile());
        when(addressMapper.selectByAddressNo("ADDR202610230001")).thenReturn(others);

        assertThatThrownBy(() -> service.detail(ACCOUNT_ID, "ADDR202610230001"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    @DisplayName("服务范围外的地址可以保存，但不允许生成下单快照")
    void outOfRangeAddressCannotSnapshot() {
        CustomerAddressEntity target = address(1L, true);
        target.setAreaMatchStatus("OUT_OF_RANGE");
        target.setServiceAreaId(null);
        when(profileService.requireUsableByAccountId(ACCOUNT_ID)).thenReturn(profile());
        when(addressMapper.selectByAddressNo("ADDR202610230001")).thenReturn(target);

        assertThatThrownBy(() -> service.requireSnapshot(ACCOUNT_ID, "ADDR202610230001"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_AREA_OUT_OF_RANGE);
    }

    private void stubRegionDictionary() {
        AdministrativeRegionEntity province = region(1L, "440000", null, "PROVINCE", "广东省");
        AdministrativeRegionEntity city = region(2L, "440100", 1L, "CITY", "广州市");
        AdministrativeRegionEntity district = region(3L, "440106", 2L, "DISTRICT", "天河区");
        when(regionDictionaryService.requireByCode("440106")).thenReturn(district);
        when(regionDictionaryService.requireById(2L)).thenReturn(city);
        when(regionDictionaryService.requireById(1L)).thenReturn(province);
    }

    private ServiceAreaMatchResult matchedResult() {
        return new ServiceAreaMatchResult(AreaMatchStatus.MATCHED, 12L, "SA202610230001",
                "天河区核心片区", CoverageLevel.DISTRICT, "440106", LocalDateTime.now(), null);
    }

    private CustomerProfileEntity profile() {
        CustomerProfileEntity profile = new CustomerProfileEntity();
        profile.setId(CUSTOMER_ID);
        profile.setAccountId(ACCOUNT_ID);
        profile.setCustomerNo("CUS202610230001");
        profile.setStatus("ACTIVE");
        return profile;
    }

    private CustomerAddressEntity address(long id, boolean isDefault) {
        CustomerAddressEntity entity = new CustomerAddressEntity();
        entity.setId(id);
        entity.setAddressNo("ADDR202610230001");
        entity.setCustomerId(CUSTOMER_ID);
        entity.setStatus("ACTIVE");
        entity.setIsDefault(isDefault);
        entity.setVersion(0L);
        entity.setDistrictCode("440106");
        return entity;
    }

    private AdministrativeRegionEntity region(long id, String code, Long parentId,
                                              String level, String name) {
        AdministrativeRegionEntity entity = new AdministrativeRegionEntity();
        entity.setId(id);
        entity.setRegionCode(code);
        entity.setParentId(parentId);
        entity.setRegionLevel(level);
        entity.setRegionName(name);
        entity.setStatus("ACTIVE");
        return entity;
    }

    private AddressCommand command(Boolean isDefault) {
        return new AddressCommand("HOME", "李女士", "+86", "13800138000", null, "440100",
                "440106", null, "示例花园", "3栋", "2单元", "8层", "801",
                "东门进入后直行100米", null, null, "门禁请联系联系人", isDefault, null);
    }
}
