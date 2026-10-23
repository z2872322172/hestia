package com.housekeeping.platform.catalog.application;

import com.housekeeping.platform.catalog.application.view.CategoryView;
import com.housekeeping.platform.catalog.domain.ServiceCategoryStatus;
import com.housekeeping.platform.catalog.persistence.entity.ServiceCategoryEntity;
import com.housekeeping.platform.catalog.persistence.mapper.ServiceCategoryMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务分类服务。
 *
 * <p>层级限制：数据库支持任意深度的自引用树，但企业首版只允许三层
 * （docs/modules/04 第 5.1 节）。限制写在业务层而不是数据库——
 * 它是产品约束，将来放开到五层只需改这个常量。</p>
 */
@Service
@RequiresPersistence
public class ServiceCategoryService {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceCategoryService.class);

    /** 最大层级（一级 = 1）。 */
    public static final int MAX_DEPTH = 3;

    private static final String CATEGORY_NO_SEQ_KEY = "SERVICE_CATEGORY";

    private static final String CATEGORY_NO_PREFIX = "CAT";

    private final ServiceCategoryMapper categoryMapper;

    private final BusinessNoGenerator businessNoGenerator;

    public ServiceCategoryService(ServiceCategoryMapper categoryMapper,
                                  BusinessNoGenerator businessNoGenerator) {
        this.categoryMapper = categoryMapper;
        this.businessNoGenerator = businessNoGenerator;
    }

    /**
     * 前台可见的分类树（自身与父链都是 ACTIVE）。
     *
     * @return 一级分类列表，子分类挂在 {@code children}
     */
    @Transactional(readOnly = true)
    public List<CategoryView> visibleTree() {
        return buildTree(categoryMapper.selectVisible());
    }

    /**
     * 运营后台的完整分类树（含停用）。
     *
     * @return 一级分类列表
     */
    @Transactional(readOnly = true)
    public List<CategoryView> adminTree() {
        return buildTree(categoryMapper.selectAllOrdered());
    }

    /**
     * 新建分类。
     *
     * @param parentNo   父分类编号，为空表示一级分类
     * @param name       分类名称
     * @param iconUrl    图标
     * @param description 描述
     * @param sortOrder  排序
     * @return 新建的分类视图
     * @throws BusinessException 父分类不存在、已停用或层级超限
     */
    @Transactional
    public CategoryView create(String parentNo, String name, String iconUrl,
                               String description, int sortOrder) {
        Long parentId = null;
        if (parentNo != null && !parentNo.isBlank()) {
            ServiceCategoryEntity parent = requireByNo(parentNo);
            if (!ServiceCategoryStatus.ACTIVE.name().equals(parent.getStatus())) {
                throw new BusinessException(ErrorCode.SERVICE_CATEGORY_DISABLED);
            }
            int parentDepth = depthOf(parent);
            if (parentDepth >= MAX_DEPTH) {
                throw new BusinessException(ErrorCode.SERVICE_CATEGORY_DEPTH_EXCEEDED);
            }
            parentId = parent.getId();
        }

        ServiceCategoryEntity entity = new ServiceCategoryEntity();
        entity.setCategoryNo(businessNoGenerator.next(CATEGORY_NO_SEQ_KEY, CATEGORY_NO_PREFIX));
        entity.setParentId(parentId);
        entity.setCategoryName(name);
        entity.setIconUrl(iconUrl);
        entity.setDescription(description);
        entity.setSortOrder(sortOrder);
        entity.setStatus(ServiceCategoryStatus.ACTIVE.name());
        categoryMapper.insert(entity);
        return toView(entity, List.of());
    }

    /**
     * 修改分类。只允许改展示信息与状态，不允许改父级。
     *
     * <p>不允许改父级是有意的：把一棵子树挂到别处会让层级深度整体变化，
     * 而层级限制是在创建时校验的，移动后可能悄悄变成四层。真要移动，
     * 走"新建 + 迁移服务项目 + 停用旧分类"更可控。</p>
     *
     * @param categoryNo 分类编号
     * @param name       名称
     * @param iconUrl    图标
     * @param description 描述
     * @param sortOrder  排序
     * @param status     目标状态
     * @return 修改后的视图
     * @throws BusinessException 分类不存在，或停用时仍有未下架服务项目
     */
    @Transactional
    public CategoryView update(String categoryNo, String name, String iconUrl,
                               String description, int sortOrder, ServiceCategoryStatus status) {
        ServiceCategoryEntity entity = requireByNo(categoryNo);

        if (status == ServiceCategoryStatus.DISABLED
                && !ServiceCategoryStatus.DISABLED.name().equals(entity.getStatus())) {
            int activeItems = categoryMapper.countActiveItems(entity.getId());
            if (activeItems > 0) {
                LOG.warn("拒绝停用分类：仍有未下架服务项目 categoryNo={} items={}", categoryNo, activeItems);
                throw new BusinessException(ErrorCode.SERVICE_CATEGORY_HAS_ACTIVE_ITEMS);
            }
        }

        entity.setCategoryName(name);
        entity.setIconUrl(iconUrl);
        entity.setDescription(description);
        entity.setSortOrder(sortOrder);
        entity.setStatus(status.name());
        categoryMapper.updateById(entity);
        return toView(entity, List.of());
    }

    /**
     * 按编号取分类。
     *
     * @param categoryNo 分类编号
     * @return 分类实体
     * @throws BusinessException 不存在
     */
    @Transactional(readOnly = true)
    public ServiceCategoryEntity requireByNo(String categoryNo) {
        ServiceCategoryEntity entity = categoryMapper.selectByCategoryNo(categoryNo);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SERVICE_CATEGORY_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 按 ID 取分类。
     *
     * @param categoryId 分类 ID
     * @return 分类实体
     * @throws BusinessException 不存在
     */
    @Transactional(readOnly = true)
    public ServiceCategoryEntity requireById(Long categoryId) {
        ServiceCategoryEntity entity = categoryMapper.selectById(categoryId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SERVICE_CATEGORY_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 计算分类所在层级（一级 = 1）。
     *
     * <p>最多向上走 {@link #MAX_DEPTH} 步就停：如果数据里已经存在环，
     * 无限向上会直接挂死请求线程。宁可算出一个偏小的层级，也不能让接口超时。</p>
     *
     * @param entity 分类
     * @return 层级
     */
    private int depthOf(ServiceCategoryEntity entity) {
        int depth = 1;
        Long parentId = entity.getParentId();
        while (parentId != null && depth < MAX_DEPTH + 1) {
            ServiceCategoryEntity parent = categoryMapper.selectById(parentId);
            if (parent == null) {
                break;
            }
            depth++;
            parentId = parent.getParentId();
        }
        return depth;
    }

    private List<CategoryView> buildTree(List<ServiceCategoryEntity> entities) {
        Map<Long, List<ServiceCategoryEntity>> byParent = new LinkedHashMap<>();
        List<ServiceCategoryEntity> roots = new ArrayList<>();
        for (ServiceCategoryEntity entity : entities) {
            if (entity.getParentId() == null) {
                roots.add(entity);
            } else {
                byParent.computeIfAbsent(entity.getParentId(), key -> new ArrayList<>()).add(entity);
            }
        }
        List<CategoryView> result = new ArrayList<>(roots.size());
        for (ServiceCategoryEntity root : roots) {
            result.add(toView(root, childrenOf(root, byParent)));
        }
        return result;
    }

    private List<CategoryView> childrenOf(ServiceCategoryEntity parent,
                                          Map<Long, List<ServiceCategoryEntity>> byParent) {
        List<ServiceCategoryEntity> children = byParent.getOrDefault(parent.getId(), List.of());
        List<CategoryView> views = new ArrayList<>(children.size());
        for (ServiceCategoryEntity child : children) {
            views.add(toView(child, childrenOf(child, byParent)));
        }
        return views;
    }

    private CategoryView toView(ServiceCategoryEntity entity, List<CategoryView> children) {
        return new CategoryView(entity.getCategoryNo(), entity.getCategoryName(), entity.getIconUrl(),
                entity.getDescription(), entity.getSortOrder() == null ? 0 : entity.getSortOrder(),
                entity.getStatus(), children);
    }
}
