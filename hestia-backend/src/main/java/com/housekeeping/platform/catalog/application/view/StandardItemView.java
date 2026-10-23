package com.housekeeping.platform.catalog.application.view;

/**
 * 服务标准检查项视图。
 *
 * @param standardGroup    分组名
 * @param itemName         检查项名称
 * @param description      说明
 * @param required         是否必做
 * @param evidenceRequired 是否必须上传照片
 * @param sortOrder        排序
 */
public record StandardItemView(String standardGroup, String itemName, String description,
                               boolean required, boolean evidenceRequired, int sortOrder) {
}
