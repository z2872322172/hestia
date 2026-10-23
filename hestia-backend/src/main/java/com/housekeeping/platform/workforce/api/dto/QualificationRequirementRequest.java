package com.housekeeping.platform.workforce.api.dto;

import com.housekeeping.platform.workforce.application.view.QualificationRequirementView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 整体替换资质要求请求。
 *
 * @param requirements 要求列表
 */
public record QualificationRequirementRequest(@Valid List<Item> requirements) {

    /**
     * 单项要求。
     *
     * @param certificateType 证书类型
     * @param required        是否必需
     */
    public record Item(@NotBlank(message = "证书类型不能为空") String certificateType, boolean required) {

        /**
         * 转应用层视图（应用层直接消费它，避免再定义一个同构类型）。
         *
         * @return 要求视图
         */
        public QualificationRequirementView toView() {
            return new QualificationRequirementView(certificateType, required);
        }
    }
}
