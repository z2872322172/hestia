package com.housekeeping.platform.catalog.application.view;

import java.util.List;

/**
 * 服务分类视图（树）。
 *
 * <p>一次返回整棵树而不是提供"按父级查询"的接口：分类总量是几十条量级，
 * 一次拉完省掉前端递归请求；等分类上千了再改成分层加载也不迟。</p>
 *
 * @param categoryNo   分类编号
 * @param categoryName 分类名称
 * @param iconUrl      图标
 * @param description  描述
 * @param sortOrder    排序值
 * @param status       状态，仅运营接口会返回非 ACTIVE
 * @param children     子分类
 */
public record CategoryView(String categoryNo, String categoryName, String iconUrl, String description,
                           int sortOrder, String status, List<CategoryView> children) {
}
