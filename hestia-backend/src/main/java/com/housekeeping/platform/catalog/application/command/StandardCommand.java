package com.housekeeping.platform.catalog.application.command;

import java.util.List;

/**
 * 服务标准整体替换命令。
 *
 * <p>与 {@link ServiceItemCommand} 里的规格一样，标准也是整体替换：
 * 排序是标准的一部分，逐条 diff 很难保证最终顺序符合运营预期。</p>
 *
 * @param items 检查项列表，顺序即展示顺序
 */
public record StandardCommand(List<StandardItemCommand> items) {

    /**
     * 检查项命令。
     *
     * @param standardGroup    分组
     * @param itemName         名称
     * @param description      说明
     * @param required         是否必做
     * @param evidenceRequired 是否必须上传照片
     * @param sortOrder        排序
     */
    public record StandardItemCommand(String standardGroup, String itemName, String description,
                                      boolean required, boolean evidenceRequired, Integer sortOrder) {
    }
}
