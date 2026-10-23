package com.housekeeping.platform.catalog.application;

import com.housekeeping.platform.catalog.application.command.PriceQuoteQuery;
import com.housekeeping.platform.catalog.application.view.PricePreviewView;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemOptionEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServiceItemOptionValueEntity;
import com.housekeeping.platform.catalog.persistence.entity.ServicePriceTierEntity;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceItemMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceItemOptionMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceItemOptionValueMapper;
import com.housekeeping.platform.catalog.persistence.mapper.ServicePriceTierMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 价格预览服务测试：覆盖规格校验的各种拒绝路径。
 *
 * <p>这些分支如果漏了，表现是"能下单但价格不对"或"下单选了不存在的规格也照算"，
 * 都属于线上最难查的那类问题，所以逐条测。</p>
 */
@ExtendWith(MockitoExtension.class)
class PriceQuoteServiceTest {

    private static final String ITEM_NO = "SVC202610230002";

    private static final long ITEM_ID = 2L;

    private static final long OPTION_ID = 20L;

    @Mock
    private ServiceItemMapper itemMapper;

    @Mock
    private ServiceItemOptionMapper optionMapper;

    @Mock
    private ServiceItemOptionValueMapper optionValueMapper;

    @Mock
    private ServicePriceTierMapper tierMapper;

    private PriceQuoteService service;

    @BeforeEach
    void setUp() {
        service = new PriceQuoteService(itemMapper, optionMapper, optionValueMapper, tierMapper);
    }

    @Test
    @DisplayName("固定价项目：无规格时按基础价计算")
    void calculatesFixedPriceWithoutOptions() {
        ServiceItemEntity item = fixedItem();
        when(itemMapper.selectPublishedByItemNo(ITEM_NO)).thenReturn(item);
        // 规格组按实体自身的 ID 查，不用常量——否则实体 ID 一改，桩就对不上
        when(optionMapper.selectActiveByItemId(item.getId())).thenReturn(List.of());

        PricePreviewView view = service.preview(ITEM_NO,
                new PriceQuoteQuery(BigDecimal.ONE, List.of()));

        assertThat(view.payableAmount()).isEqualByComparingTo("199.00");
        assertThat(view.baseAmount()).isEqualByComparingTo("199.00");
        assertThat(view.addonAmount()).isEqualByComparingTo("0.00");
        assertThat(view.serviceItemVersion()).isZero();
    }

    @Test
    @DisplayName("必选规格未选择时拒绝")
    void rejectsMissingRequiredOption() {
        stubAreaOption();

        assertThatThrownBy(() -> service.preview(ITEM_NO,
                new PriceQuoteQuery(BigDecimal.ONE, List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SERVICE_OPTION_REQUIRED);
    }

    @Test
    @DisplayName("单选组选了多个值时拒绝")
    void rejectsMultipleValuesForSingleOption() {
        stubAreaOption();

        List<PriceQuoteQuery.OptionSelection> selections = List.of(
                new PriceQuoteQuery.OptionSelection("AREA", "AREA_0_60"),
                new PriceQuoteQuery.OptionSelection("AREA", "AREA_60_90"));

        assertThatThrownBy(() -> service.preview(ITEM_NO,
                new PriceQuoteQuery(BigDecimal.ONE, selections)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SERVICE_OPTION_SELECTION_LIMIT);
    }

    @Test
    @DisplayName("提交了服务项目没有的规格组时拒绝")
    void rejectsUnknownOption() {
        stubAreaOption();

        List<PriceQuoteQuery.OptionSelection> selections = List.of(
                new PriceQuoteQuery.OptionSelection("NOT_EXIST", "ANY"));

        assertThatThrownBy(() -> service.preview(ITEM_NO,
                new PriceQuoteQuery(BigDecimal.ONE, selections)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SERVICE_OPTION_INVALID);
    }

    @Test
    @DisplayName("规格值不属于该规格组时拒绝")
    void rejectsUnknownOptionValue() {
        stubAreaOption();

        List<PriceQuoteQuery.OptionSelection> selections = List.of(
                new PriceQuoteQuery.OptionSelection("AREA", "AREA_999"));

        assertThatThrownBy(() -> service.preview(ITEM_NO,
                new PriceQuoteQuery(BigDecimal.ONE, selections)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SERVICE_OPTION_INVALID);
    }

    @Test
    @DisplayName("同一个规格值重复提交时拒绝")
    void rejectsDuplicateSelection() {
        stubAreaOption();

        List<PriceQuoteQuery.OptionSelection> selections = List.of(
                new PriceQuoteQuery.OptionSelection("AREA", "AREA_0_60"),
                new PriceQuoteQuery.OptionSelection("AREA", "AREA_0_60"));

        assertThatThrownBy(() -> service.preview(ITEM_NO,
                new PriceQuoteQuery(BigDecimal.ONE, selections)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SERVICE_OPTION_INVALID);
    }

    @Test
    @DisplayName("服务项目未发布或不存在时统一报找不到")
    void rejectsUnpublishedItem() {
        when(itemMapper.selectPublishedByItemNo(ITEM_NO)).thenReturn(null);

        assertThatThrownBy(() -> service.preview(ITEM_NO,
                new PriceQuoteQuery(BigDecimal.ONE, List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SERVICE_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("面积档位按规格值数值选档：60-90 档 499 元")
    void selectsAreaTierByNumericValue() {
        stubAreaOption();
        when(tierMapper.selectActiveByItemAndType(ITEM_ID, "AREA_TIER")).thenReturn(List.of(
                tier("0", "60", "399.00"),
                tier("60", "90", "499.00")));

        List<PriceQuoteQuery.OptionSelection> selections = List.of(
                new PriceQuoteQuery.OptionSelection("AREA", "AREA_60_90"));

        PricePreviewView view = service.preview(ITEM_NO,
                new PriceQuoteQuery(BigDecimal.ONE, selections));

        assertThat(view.baseAmount()).isEqualByComparingTo("499.00");
        assertThat(view.payableAmount()).isEqualByComparingTo("499.00");
    }

    private void stubAreaOption() {
        when(itemMapper.selectPublishedByItemNo(ITEM_NO)).thenReturn(areaTierItem());
        ServiceItemOptionEntity option = new ServiceItemOptionEntity();
        option.setId(OPTION_ID);
        option.setServiceItemId(ITEM_ID);
        option.setOptionCode("AREA");
        option.setOptionName("房屋面积");
        option.setSelectionType("SINGLE");
        option.setRequired(true);
        option.setMinSelect(1);
        option.setMaxSelect(1);
        option.setStatus("ACTIVE");
        when(optionMapper.selectActiveByItemId(ITEM_ID)).thenReturn(List.of(option));

        when(optionValueMapper.selectActiveByOptionIds(List.of(OPTION_ID))).thenReturn(List.of(
                value("AREA_0_60", "60 平米以内", "0"),
                value("AREA_60_90", "60-90 平米", "60")));
    }

    private ServiceItemEntity fixedItem() {
        ServiceItemEntity item = new ServiceItemEntity();
        item.setId(1L);
        item.setItemNo("SVC202610230001");
        item.setItemName("日常保洁·两居室");
        item.setServiceUnit("ORDER");
        item.setPricingMode("FIXED");
        item.setBasePrice(new BigDecimal("199.00"));
        item.setDurationMinutes(240);
        item.setMinQuantity(BigDecimal.ONE);
        item.setMaxQuantity(BigDecimal.ONE);
        item.setVersion(0L);
        item.setStatus("PUBLISHED");
        return item;
    }

    private ServiceItemEntity areaTierItem() {
        ServiceItemEntity item = new ServiceItemEntity();
        item.setId(ITEM_ID);
        item.setItemNo(ITEM_NO);
        item.setItemName("深度保洁·按面积");
        item.setServiceUnit("SQUARE_METER");
        item.setPricingMode("AREA_TIER");
        item.setBasePrice(BigDecimal.ZERO);
        item.setDurationMinutes(300);
        item.setMinQuantity(BigDecimal.ONE);
        item.setMaxQuantity(BigDecimal.ONE);
        item.setVersion(0L);
        item.setStatus("PUBLISHED");
        return item;
    }

    private ServiceItemOptionValueEntity value(String code, String name, String numeric) {
        ServiceItemOptionValueEntity value = new ServiceItemOptionValueEntity();
        value.setOptionId(OPTION_ID);
        value.setValueCode(code);
        value.setValueName(name);
        value.setNumericValue(new BigDecimal(numeric));
        value.setExtraPrice(BigDecimal.ZERO);
        value.setDurationDelta(0);
        value.setStatus("ACTIVE");
        return value;
    }

    private ServicePriceTierEntity tier(String min, String max, String fixedPrice) {
        ServicePriceTierEntity tier = new ServicePriceTierEntity();
        tier.setServiceItemId(ITEM_ID);
        tier.setTierType("AREA_TIER");
        tier.setMinValue(new BigDecimal(min));
        tier.setMaxValue(max == null ? null : new BigDecimal(max));
        tier.setFixedPrice(new BigDecimal(fixedPrice));
        tier.setStatus("ACTIVE");
        return tier;
    }
}
