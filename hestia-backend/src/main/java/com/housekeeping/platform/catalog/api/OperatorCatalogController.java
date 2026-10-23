package com.housekeeping.platform.catalog.api;

import com.housekeeping.platform.catalog.api.dto.CreateCategoryRequest;
import com.housekeeping.platform.catalog.api.dto.ServiceItemRequest;
import com.housekeeping.platform.catalog.api.dto.StandardRequest;
import com.housekeeping.platform.catalog.api.dto.UpdateCategoryRequest;
import com.housekeeping.platform.catalog.application.ServiceCatalogQueryService;
import com.housekeeping.platform.catalog.application.ServiceCategoryService;
import com.housekeeping.platform.catalog.application.ServiceItemAdminService;
import com.housekeeping.platform.catalog.application.view.CategoryView;
import com.housekeeping.platform.catalog.application.view.ServiceItemDetailView;
import com.housekeeping.platform.catalog.application.view.ServiceItemSummaryView;
import com.housekeeping.platform.catalog.application.view.StandardItemView;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.pagination.PageQuery;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务目录运营接口。
 *
 * <p>权限由 {@code SecurityConfig} 的路径规则统一控制（OPERATOR / ADMIN），
 * 方法上不再重复声明，避免两处配置不一致时出现"看起来限制了其实没限制"。</p>
 */
@RestController
@RequestMapping("/api/v1/operator")
@RequiresPersistence
@Tag(name = "服务目录（运营）", description = "分类与维护服务项目、发布下架、服务标准")
public class OperatorCatalogController {

    private final ServiceCategoryService categoryService;

    private final ServiceItemAdminService itemAdminService;

    private final ServiceCatalogQueryService catalogQueryService;

    public OperatorCatalogController(ServiceCategoryService categoryService,
                                     ServiceItemAdminService itemAdminService,
                                     ServiceCatalogQueryService catalogQueryService) {
        this.categoryService = categoryService;
        this.itemAdminService = itemAdminService;
        this.catalogQueryService = catalogQueryService;
    }

    /**
     * 完整分类树（含停用）。
     *
     * @return 一级分类列表
     */
    @GetMapping("/service-categories")
    @Operation(summary = "分类树（含停用）")
    public Result<List<CategoryView>> categories() {
        return Result.ok(categoryService.adminTree());
    }

    /**
     * 新建分类。
     *
     * @param request 请求体
     * @return 新建的分类
     */
    @PostMapping("/service-categories")
    @Operation(summary = "新建分类")
    public Result<CategoryView> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return Result.ok(categoryService.create(request.parentNo(), request.categoryName(),
                request.iconUrl(), request.description(),
                request.sortOrder() == null ? 0 : request.sortOrder()));
    }

    /**
     * 修改分类。
     *
     * @param categoryNo 分类编号
     * @param request    请求体
     * @return 修改后的分类
     */
    @PutMapping("/service-categories/{categoryNo}")
    @Operation(summary = "修改分类")
    public Result<CategoryView> updateCategory(
            @PathVariable @Size(max = 32, message = "分类编号过长") String categoryNo,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return Result.ok(categoryService.update(categoryNo, request.categoryName(),
                request.iconUrl(), request.description(),
                request.sortOrder() == null ? 0 : request.sortOrder(), request.status()));
    }

    /**
     * 服务项目分页列表。
     *
     * @param page    页码
     * @param size    每页条数
     * @param status  状态过滤
     * @param keyword 关键字
     * @return 分页结果
     */
    @GetMapping("/service-items")
    @Operation(summary = "服务项目分页列表")
    public Result<PageResult<ServiceItemSummaryView>> items(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) @Size(max = 16, message = "状态过长") String status,
            @RequestParam(required = false) @Size(max = 64, message = "关键字过长") String keyword) {
        PageQuery pageQuery = PageQuery.of(page, size);
        return Result.ok(itemAdminService.page(pageQuery.getPage(), pageQuery.getSize(), status, keyword));
    }

    /**
     * 新建草稿服务项目。
     *
     * @param request 请求体
     * @return 详情
     */
    @PostMapping("/service-items")
    @Operation(summary = "新建服务项目（草稿）")
    public Result<ServiceItemDetailView> createItem(@Valid @RequestBody ServiceItemRequest request) {
        return Result.ok(itemAdminService.create(request.toCommand()));
    }

    /**
     * 服务项目详情（任意状态）。
     *
     * @param itemNo 服务项目编号
     * @return 详情
     */
    @GetMapping("/service-items/{itemNo}")
    @Operation(summary = "服务项目详情（任意状态）")
    public Result<ServiceItemDetailView> itemDetail(
            @PathVariable @Size(max = 32, message = "服务项目编号过长") String itemNo) {
        return Result.ok(catalogQueryService.adminDetail(itemNo));
    }

    /**
     * 修改服务项目。
     *
     * <p>版本号从请求体的 {@code version} 取，必填。缺失时报参数校验失败，
     * 而不是"默认取当前版本"——后者会让乐观锁彻底失效。</p>
     *
     * @param itemNo  服务项目编号
     * @param request 请求体
     * @return 详情
     */
    @PutMapping("/service-items/{itemNo}")
    @Operation(summary = "修改服务项目")
    public Result<ServiceItemDetailView> updateItem(
            @PathVariable @Size(max = 32, message = "服务项目编号过长") String itemNo,
            @Valid @RequestBody ServiceItemRequest request) {
        if (request.version() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return Result.ok(itemAdminService.update(itemNo, request.version(), request.toCommand()));
    }

    /**
     * 发布服务项目。
     *
     * @param itemNo 服务项目编号
     * @return 详情
     */
    @PostMapping("/service-items/{itemNo}/publish")
    @Operation(summary = "发布服务项目")
    public Result<ServiceItemDetailView> publish(
            @PathVariable @Size(max = 32, message = "服务项目编号过长") String itemNo) {
        return Result.ok(itemAdminService.publish(itemNo));
    }

    /**
     * 下架服务项目。
     *
     * @param itemNo 服务项目编号
     * @return 详情
     */
    @PostMapping("/service-items/{itemNo}/offline")
    @Operation(summary = "下架服务项目")
    public Result<ServiceItemDetailView> offline(
            @PathVariable @Size(max = 32, message = "服务项目编号过长") String itemNo) {
        return Result.ok(itemAdminService.offline(itemNo));
    }

    /**
     * 查询服务标准。
     *
     * @param itemNo 服务项目编号
     * @return 检查项列表
     */
    @GetMapping("/service-items/{itemNo}/standards")
    @Operation(summary = "查询服务标准")
    public Result<List<StandardItemView>> standards(
            @PathVariable @Size(max = 32, message = "服务项目编号过长") String itemNo) {
        return Result.ok(catalogQueryService.adminDetail(itemNo).standards());
    }

    /**
     * 整体替换服务标准。
     *
     * @param itemNo  服务项目编号
     * @param request 请求体
     * @return 替换后的检查项
     */
    @PutMapping("/service-items/{itemNo}/standards")
    @Operation(summary = "替换服务标准")
    public Result<List<StandardItemView>> replaceStandards(
            @PathVariable @Size(max = 32, message = "服务项目编号过长") String itemNo,
            @Valid @RequestBody StandardRequest request) {
        return Result.ok(itemAdminService.replaceStandards(itemNo, request.toCommand()));
    }
}
