package com.housekeeping.platform.bootstrap.web;

import com.housekeeping.platform.bootstrap.web.vo.SmokeFileVo;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.infrastructure.storage.minio.SmokeFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对象存储冒烟接口：验证"上传 → 元数据落库 → 生成短期访问地址"。
 *
 * <p>只允许 JPEG / PNG / PDF 且不超过配置上限，生产环境随 smoke 接口一并关闭。</p>
 */
@RestController
@RequestMapping("/api/v1/system/smoke")
@ConditionalOnExpression("${app.feature.persistence-enabled:true} and ${app.storage.enabled:true}")
@Tag(name = "系统冒烟-文件", description = "上传文件到公开桶并返回预签名地址")
public class SystemSmokeFileController {

    private final SmokeFileService smokeFileService;

    public SystemSmokeFileController(SmokeFileService smokeFileService) {
        this.smokeFileService = smokeFileService;
    }

    /**
     * 上传一个冒烟文件。
     *
     * @param file 上传文件，表单字段名固定为 {@code file}
     * @return 对象键、摘要与短期访问地址
     */
    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传冒烟文件", description = "仅支持 JPEG/PNG/PDF，上传到公开桶并写入 file_object")
    public Result<SmokeFileVo> upload(@RequestPart("file") MultipartFile file) {
        SmokeFileService.SmokeFileResult result = smokeFileService.upload(file);
        return Result.ok(new SmokeFileVo(
                result.bucket(),
                result.objectKey(),
                result.sizeBytes(),
                result.sha256(),
                result.url(),
                result.expiresInSeconds()));
    }
}
