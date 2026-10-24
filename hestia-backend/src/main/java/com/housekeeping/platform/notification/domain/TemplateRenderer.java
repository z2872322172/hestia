package com.housekeeping.platform.notification.domain;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知模板渲染纯函数（docs/modules/12 第 8 节）。
 *
 * <p>占位符语法：{@code {variableName}}。渲染失败不抛异常，而是返回
 * {@link RenderResult#ok()} 为 false 的结果——渲染失败是<b>任务级失败</b>
 * （任务进 FAILED / 重试），绝不能让异常打断事件消费或把半渲染的内容发出去。</p>
 *
 * <p>两条硬规则：</p>
 * <ul>
 *   <li><b>敏感变量拦截</b>：模板引用禁用变量（完整地址、手机号、证件号等）时渲染失败。
 *       拦截写在渲染层而不是模板维护层，因为模板随时可被运营改坏，渲染时兜底才能保证
 *       "敏感内容永远出不了站内消息"这条不变量。</li>
 *   <li><b>缺变量即失败</b>：占位符在变量表里找不到时渲染失败，保留占位符原文发送
 *       会把 {@code {orderNo}} 这样的半成品推给用户，宁可失败重试。</li>
 * </ul>
 */
public final class TemplateRenderer {

    /** 模板占位符：{variableName}，变量名允许字母、数字、下划线。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    /**
     * 禁用的变量名（docs/modules/12 第 8 节）。
     *
     * <p>按"变量名语义"拦截而不是按值格式校验——同一个值换个变量名传入也拦不住格式，
     * 所以约束链条是：上游事件载荷约定不带敏感明文（BookingEventPublisher 注释），
     * 渲染层再按变量名兜底，两层防线缺一不可。</p>
     */
    private static final Set<String> FORBIDDEN_VARIABLES = Set.of(
            "address", "fullAddress", "addressDetail", "doorPlate",
            "phone", "mobile", "contactPhone", "telephone",
            "idCard", "idNumber", "identityNo",
            "password", "secret", "apiKey", "payKey",
            "photoUrl", "evidenceUrl", "fileUrl");

    private TemplateRenderer() {
    }

    /**
     * 渲染标题与正文模板。
     *
     * @param titleTemplate   标题模板
     * @param contentTemplate 正文模板
     * @param variables       变量表（允许 null，等价于空表）
     * @return 渲染结果；失败时 {@code ok=false} 且带原因
     */
    public static RenderResult render(String titleTemplate,
                                      String contentTemplate,
                                      Map<String, Object> variables) {
        Map<String, Object> vars = variables == null ? Map.of() : variables;
        String forbidden = findForbidden(titleTemplate, vars);
        if (forbidden == null) {
            forbidden = findForbidden(contentTemplate, vars);
        }
        if (forbidden != null) {
            return RenderResult.fail("模板引用禁用变量：" + forbidden);
        }
        try {
            String title = substitute(titleTemplate, vars);
            String content = substitute(contentTemplate, vars);
            return RenderResult.ok(title, content);
        } catch (MissingVariableException exception) {
            return RenderResult.fail("占位符缺少变量：{" + exception.variable + "}");
        }
    }

    /**
     * 检查模板是否引用了禁用变量（模板保存时的前置校验入口）。
     *
     * <p>渲染时同样会拦截，这里提前一步给运营即时反馈，
     * 不必等一条任务失败后才暴露。</p>
     *
     * @param template 标题或正文模板
     * @return 引用的禁用变量名；没有则返回 null
     */
    public static String findForbiddenVariable(String template) {
        return findForbidden(template, Map.of());
    }

    /**
     * 扫描模板中引用的禁用变量。
     *
     * <p>只看模板占位符引用了什么，不扫描变量表本身：载荷允许携带内部字段，
     * 只有"被模板渲染进正文"才构成泄露。</p>
     */
    private static String findForbidden(String template, Map<String, Object> vars) {
        if (template == null || template.isEmpty()) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (FORBIDDEN_VARIABLES.contains(name)) {
                return name;
            }
        }
        return null;
    }

    /**
     * 执行替换。变量值统一 {@code String.valueOf}（事件载荷经 JSON 往返后全是字符串/数字），
     * null 值按缺变量处理而不是渲染出 "null"。
     */
    private static String substitute(String template, Map<String, Object> vars) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder(template.length() + 32);
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = vars.get(name);
            if (value == null) {
                throw new MissingVariableException(name);
            }
            String text = String.valueOf(value);
            // 替换文本里的 $ 与 \ 是 Matcher.appendReplacement 的特殊字符，需转义
            matcher.appendReplacement(result, Matcher.quoteReplacement(text));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** 占位符缺变量的内部信号，只在渲染器内部使用。 */
    private static final class MissingVariableException extends RuntimeException {

        private final String variable;

        private MissingVariableException(String variable) {
            super("缺少模板变量 " + variable);
            this.variable = variable;
        }
    }

    /**
     * 渲染结果值对象：成功带标题正文，失败带原因。
     */
    public record RenderResult(boolean ok, String title, String content, String errorMessage) {

        static RenderResult ok(String title, String content) {
            return new RenderResult(true, title, content, null);
        }

        static RenderResult fail(String errorMessage) {
            return new RenderResult(false, null, null, errorMessage);
        }
    }
}
