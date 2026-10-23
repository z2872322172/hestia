package com.housekeeping.platform.catalog.api.dto;

import com.housekeeping.platform.catalog.application.command.StandardCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 服务标准整体替换请求。
 *
 * @param items 检查项，顺序即展示顺序
 */
public record StandardRequest(@Valid List<Item> items) {

    /**
     * 转换为应用层命令。
     *
     * @return 命令
     */
    public StandardCommand toCommand() {
        List<StandardCommand.StandardItemCommand> commands = items == null
                ? List.of()
                : items.stream()
                        .map(item -> new StandardCommand.StandardItemCommand(item.standardGroup(),
                                item.itemName(), item.description(), item.required(),
                                item.evidenceRequired(), item.sortOrder()))
                        .toList();
        return new StandardCommand(commands);
    }

    /**
     * 检查项请求。
     *
     * @param standardGroup    分组
     * @param itemName         名称
     * @param description      说明
     * @param required         是否必做
     * @param evidenceRequired 是否必须上传照片
     * @param sortOrder        排序
     */
    public record Item(
            @Size(max = 64, message = "分组名称过长") String standardGroup,
            @NotBlank(message = "检查项名称不能为空") @Size(max = 128, message = "检查项名称过长") String itemName,
            @Size(max = 500, message = "检查项说明过长") String description,
            boolean required,
            boolean evidenceRequired,
            Integer sortOrder) {
    }
}
