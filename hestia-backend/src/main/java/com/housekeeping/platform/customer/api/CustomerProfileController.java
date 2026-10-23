package com.housekeeping.platform.customer.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.common.security.CurrentAccountResolver;
import com.housekeeping.platform.customer.application.CustomerProfileService;
import com.housekeeping.platform.customer.application.view.CustomerProfileView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户档案接口。
 *
 * <p>本批只提供查询：{@code customer_profile} 当前只有业务编号与状态，
 * 没有可修改的展示字段。等真正的业务展示信息（如昵称、备注）确定后再加
 * {@code PUT /customer/profile}，避免先造一个改不动任何字段的接口。</p>
 */
@RestController
@RequestMapping("/api/v1/customer/profile")
@RequiresPersistence
@Tag(name = "客户-档案", description = "客户业务档案查询")
public class CustomerProfileController {

    private final CustomerProfileService profileService;

    private final CurrentAccountResolver currentAccountResolver;

    public CustomerProfileController(CustomerProfileService profileService,
                                     CurrentAccountResolver currentAccountResolver) {
        this.profileService = profileService;
        this.currentAccountResolver = currentAccountResolver;
    }

    /**
     * 查询当前账号的客户档案。
     *
     * @return 客户档案
     */
    @GetMapping
    @Operation(summary = "查询我的客户档案")
    public Result<CustomerProfileView> profile() {
        return Result.ok(profileService.viewOf(currentAccountResolver.requireAccountId()));
    }
}
