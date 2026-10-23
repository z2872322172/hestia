package com.housekeeping.platform.catalog.api;

import com.housekeeping.platform.catalog.application.PriceQuoteService;
import com.housekeeping.platform.catalog.application.ServiceCatalogQueryService;
import com.housekeeping.platform.catalog.application.ServiceCategoryService;
import com.housekeeping.platform.catalog.application.view.CategoryView;
import com.housekeeping.platform.catalog.application.view.PricePreviewView;
import com.housekeeping.platform.catalog.application.view.ServiceItemDetailView;
import com.housekeeping.platform.catalog.application.view.ServiceItemSummaryView;
import com.housekeeping.platform.catalog.api.dto.PricePreviewRequest;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务目录公共接口（无需登录）。
 *
 * <p>只暴露已发布的服务项目：草稿与已下架项目在这里一律表现为"不存在"，
 * 与 {@code /api/v1/customer/addresses/{addressNo}} 的越权处理保持同一口径。</p>
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiresPersistence
@Tag(name = "服务目录（公共）", description = "分类树、服务项目、价格预览")
public class PublicCatalogController {

    private final ServiceCategoryService categoryService;

    private final ServiceCatalogQueryService catalogQueryService;

    private final PriceQuoteService priceQuoteService;

    public PublicCatalogController(ServiceCategoryService categoryService,
                                   ServiceCatalogQueryService catalogQueryService,
                                   PriceQuoteService priceQuoteService) {
        this.categoryService = categoryService;
        this.catalogQueryService = catalogQueryService;
        this.priceQuoteService = priceQuoteService;
    }

    /**
     * 服务分类树。
     *
     * @return 一级分类列表
     */
    @GetMapping("/service-categories")
    @Operation(summary = "服务分类树")
    public Result<List<CategoryView>> categories() {
        return Result.ok(categoryService.visibleTree());
    }

    /**
     * 服务项目列表。
     *
     * @param categoryNo 分类编号，为空表示全部
     * @return 摘要列表
     */
    @GetMapping("/service-items")
    @Operation(summary = "服务项目列表")
    public Result<List<ServiceItemSummaryView>> items(
            @RequestParam(required = false) @Size(max = 32, message = "分类编号过长") String categoryNo) {
        return Result.ok(catalogQueryService.listPublished(categoryNo));
    }

    /**
     * 服务项目详情。
     *
     * @param itemNo 服务项目编号
     * @return 详情
     */
    @GetMapping("/service-items/{itemNo}")
    @Operation(summary = "服务项目详情")
    public Result<ServiceItemDetailView> detail(
            @PathVariable @Size(max = 32, message = "服务项目编号过长") String itemNo) {
        return Result.ok(catalogQueryService.detail(itemNo));
    }

    /**
     * 价格预览。
     *
     * <p>金额一律服务端计算；请求体里没有金额字段，客户端无从覆盖。</p>
     *
     * @param itemNo  服务项目编号
     * @param request 数量与已选规格
     * @return 价格明细
     */
    @PostMapping("/service-items/{itemNo}/price-preview")
    @Operation(summary = "价格预览")
    public Result<PricePreviewView> pricePreview(
            @PathVariable @Size(max = 32, message = "服务项目编号过长") String itemNo,
            @Valid @RequestBody PricePreviewRequest request) {
        return Result.ok(priceQuoteService.preview(itemNo, request.toQuery()));
    }
}
