package com.housekeeping.platform.location.api;

import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.location.api.dto.RegionImportRequest;
import com.housekeeping.platform.location.application.RegionDictionaryService;
import com.housekeeping.platform.location.application.command.RegionImportItem;
import com.housekeeping.platform.location.application.view.RegionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理员行政区字典接口。
 *
 * <p>行政区编码是国家标准，只有管理员能导入；运营只能读取，不能修改编码本身。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/administrative-regions")
@Validated
@RequiresPersistence
@Tag(name = "管理员-行政区", description = "行政区字典导入与查询")
public class AdminRegionController {

    private final RegionDictionaryService regionDictionaryService;

    public AdminRegionController(RegionDictionaryService regionDictionaryService) {
        this.regionDictionaryService = regionDictionaryService;
    }

    /**
     * 幂等导入行政区。
     *
     * @param requests 导入项列表
     * @return 受影响条数
     */
    @PostMapping("/import")
    @Operation(summary = "导入行政区（幂等）")
    public Result<Map<String, Integer>> importRegions(@Valid @RequestBody List<RegionImportRequest> requests) {
        List<RegionImportItem> items = requests.stream()
                .map(request -> new RegionImportItem(request.regionCode(), request.parentCode(),
                        request.regionLevel(), request.regionName(), request.shortName(), request.sortOrder()))
                .toList();
        int affected = regionDictionaryService.importRegions(items);
        return Result.ok(Map.of("affected", affected));
    }

    /**
     * 查询下级行政区。
     *
     * @param parentCode 上级行政区编码，为空时返回省级
     * @return 行政区列表
     */
    @GetMapping
    @Operation(summary = "查询下级行政区")
    public Result<List<RegionView>> list(
            @RequestParam(required = false) @Size(max = 12, message = "行政区编码过长") String parentCode) {
        return Result.ok(regionDictionaryService.listChildren(parentCode));
    }
}
