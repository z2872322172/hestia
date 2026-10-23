package com.housekeeping.platform.customer.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.customer.api.dto.CustomerAddressRequest;
import com.housekeeping.platform.customer.application.CustomerAddressService;
import com.housekeeping.platform.customer.application.command.AddressCommand;
import com.housekeeping.platform.customer.application.view.AddressSnapshot;
import com.housekeeping.platform.customer.application.view.AddressView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户地址接口。
 *
 * <p>所有接口都<b>不接受</b> {@code customerId} 参数：当前客户由安全上下文里的账号解析，
 * 客户端伪造 ID 无法访问他人地址（docs/modules/02 第 19.3 节）。</p>
 */
@RestController
@RequestMapping("/api/v1/customer/addresses")
@Validated
@RequiresPersistence
@Tag(name = "客户-地址", description = "服务地址维护与范围校验")
public class CustomerAddressController {

    private final CustomerAddressService addressService;

    private final CurrentAccountResolver currentAccountResolver;

    public CustomerAddressController(CustomerAddressService addressService,
                                     CurrentAccountResolver currentAccountResolver) {
        this.addressService = addressService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 我的地址列表。
     *
     * @return 地址列表
     */
    @GetMapping
    @Operation(summary = "地址列表")
    public Result<List<AddressView>> list() {
        return Result.ok(addressService.list(currentAccountResolver.requireAccountId()));
    }

    /**
     * 新增地址。
     *
     * @param request 请求体
     * @return 新建地址
     */
    @PostMapping
    @Operation(summary = "新增地址")
    public Result<AddressView> create(@Valid @RequestBody CustomerAddressRequest request) {
        return Result.ok(addressService.create(currentAccountResolver.requireAccountId(), toCommand(request)));
    }

    /**
     * 地址详情。
     *
     * @param addressNo 地址编号
     * @return 地址
     */
    @GetMapping("/{addressNo}")
    @Operation(summary = "地址详情")
    public Result<AddressView> detail(@PathVariable @Size(max = 32) String addressNo) {
        return Result.ok(addressService.detail(currentAccountResolver.requireAccountId(), addressNo));
    }

    /**
     * 修改地址。
     *
     * @param addressNo 地址编号
     * @param request   请求体（含版本号）
     * @return 修改后的地址
     */
    @PutMapping("/{addressNo}")
    @Operation(summary = "修改地址")
    public Result<AddressView> update(@PathVariable @Size(max = 32) String addressNo,
                                      @Valid @RequestBody CustomerAddressRequest request) {
        return Result.ok(addressService.update(currentAccountResolver.requireAccountId(),
                addressNo, toCommand(request)));
    }

    /**
     * 删除地址（软删除）。
     *
     * @param addressNo 地址编号
     * @return 空结果
     */
    @DeleteMapping("/{addressNo}")
    @Operation(summary = "删除地址")
    public Result<Void> delete(@PathVariable @Size(max = 32) String addressNo) {
        addressService.delete(currentAccountResolver.requireAccountId(), addressNo);
        return Result.ok();
    }

    /**
     * 设为默认地址。
     *
     * @param addressNo 地址编号
     * @return 设置后的地址
     */
    @PutMapping("/{addressNo}/default")
    @Operation(summary = "设为默认地址")
    public Result<AddressView> setDefault(@PathVariable @Size(max = 32) String addressNo) {
        return Result.ok(addressService.setDefault(currentAccountResolver.requireAccountId(), addressNo));
    }

    /**
     * 重新校验服务范围。
     *
     * @param addressNo 地址编号
     * @return 更新后的地址
     */
    @PostMapping("/{addressNo}/availability-check")
    @Operation(summary = "服务范围校验")
    public Result<AddressView> checkAvailability(@PathVariable @Size(max = 32) String addressNo) {
        return Result.ok(addressService.checkAvailability(currentAccountResolver.requireAccountId(), addressNo));
    }

    /**
     * 生成下单地址快照（内部契约，供订单模块在批次 3 调用）。
     *
     * <p>当前挂在客户自助路径下：只有地址所有者能取自己的快照。
     * 批次 3 订单模块需要的是"系统代取"，届时改为内部服务调用而不是开放新接口。</p>
     *
     * @param addressNo 地址编号
     * @return 地址快照
     */
    @GetMapping("/{addressNo}/snapshot")
    @Operation(summary = "地址快照")
    public Result<AddressSnapshot> snapshot(@PathVariable @Size(max = 32) String addressNo) {
        return Result.ok(addressService.requireSnapshot(currentAccountResolver.requireAccountId(), addressNo));
    }

    private AddressCommand toCommand(CustomerAddressRequest request) {
        return new AddressCommand(request.label(), request.contactName(), request.contactCountryCode(),
                request.contactPhone(), request.provinceCode(), request.cityCode(), request.districtCode(),
                request.streetCode(), request.communityName(), request.buildingNo(), request.unitNo(),
                request.floorNo(), request.roomNo(), request.detailAddress(), request.longitude(),
                request.latitude(), request.accessNote(), request.isDefault(), request.version());
    }
}
