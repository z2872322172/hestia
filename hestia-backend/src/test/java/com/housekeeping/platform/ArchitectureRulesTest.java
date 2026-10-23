package com.housekeeping.platform;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构边界测试。
 *
 * <p>这些规则在业务模块为空时是"预防性"的，一旦后续批次把 Mapper 直接注入 Controller
 * 或让 common 反向依赖业务包，测试会立刻失败。架构约束靠测试守住，不靠自觉。</p>
 *
 * <p>空包说明：业务模块（identity/customer/...）在批次 1 之后才创建，
 * 因此涉及这些包的规则使用 {@code allowEmptyShould(true)}，
 * 否则 ArchUnit 会以"没有匹配到任何类"判失败。</p>
 */
@AnalyzeClasses(packages = "com.housekeeping.platform", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    /**
     * common 是跨模块技术能力，不能反向依赖基础设施实现。
     */
    @ArchTest
    static final ArchRule common_should_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("..common..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .because("common 必须是最底层，基础设施实现变化不应影响通用能力");

    /**
     * common 不能依赖任何业务模块（当前业务包为空，规则为后续批次预留）。
     */
    @ArchTest
    static final ArchRule common_should_not_depend_on_business_modules =
            noClasses().that().resideInAPackage("..common..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..identity..", "..customer..", "..catalog..", "..workforce..",
                            "..scheduling..", "..booking..", "..dispatch..", "..payment..",
                            "..fulfillment..", "..servicequality..", "..notification..",
                            "..operations..", "..audit..", "..file..")
                    .because("通用能力一旦依赖业务模块就无法被其他模块复用");

    /**
     * API 层不得直接访问 Mapper，必须经过应用服务。
     */
    @ArchTest
    static final ArchRule api_should_not_depend_on_mapper =
            noClasses().that().resideInAPackage("..api..")
                    .should().dependOnClassesThat().resideInAPackage("..persistence.mapper..")
                    .because("Controller 只做协议适配，直接查库会绕过事务、权限与幂等")
                    .allowEmptyShould(true);

    /**
     * 领域层不得依赖 Spring Web / Messaging，保证领域规则可以脱离容器单测。
     */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_web_or_messaging =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.web..", "org.springframework.messaging..")
                    .because("领域模型不应被传输层协议绑架")
                    .allowEmptyShould(true);

    /**
     * REST 控制器只能出现在 web/api 包下，禁止出现全局平铺的 controller 包。
     */
    @ArchTest
    static final ArchRule rest_controllers_should_reside_in_web_packages =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().resideInAnyPackage("..bootstrap.web..", "..api..")
                    .because("按业务模块垂直分包，禁止全局 controller 目录");

    /**
     * 禁止出现全局平铺的 service / mapper 包。
     */
    @ArchTest
    static final ArchRule no_global_flat_packages =
            noClasses().should().resideInAnyPackage(
                            "com.housekeeping.platform.controller",
                            "com.housekeeping.platform.service",
                            "com.housekeeping.platform.mapper")
                    .because("所有能力必须归属到具体模块或技术分层下");
}
