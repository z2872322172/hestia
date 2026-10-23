package com.housekeeping.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.bootstrap.web.TraceIdFilter;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.Result;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统一响应契约测试。
 *
 * <p>锁定四件事：响应字段固定为 code/message/data/traceId；HTTP 状态码与错误码匹配；
 * traceId 可被调用方透传；未预期异常不泄露内部信息。</p>
 */
class ResultContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    @DisplayName("成功响应包含 code/message/data/traceId，并回写 X-Trace-Id 响应头")
    void successResponseFollowsContract() throws Exception {
        mockMvc.perform(get("/probe/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.value").value("hello"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("调用方透传的 traceId 必须被沿用，便于跨系统串联日志")
    void inboundTraceIdIsPropagated() throws Exception {
        mockMvc.perform(get("/probe/ok").header("X-Trace-Id", "fixed-trace-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value("fixed-trace-001"))
                .andExpect(header().string("X-Trace-Id", "fixed-trace-001"));
    }

    @Test
    @DisplayName("非法 traceId 会被丢弃并重新生成，防止日志注入")
    void invalidTraceIdIsReplaced() throws Exception {
        mockMvc.perform(get("/probe/ok").header("X-Trace-Id", "bad trace id!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(org.hamcrest.Matchers.not("bad trace id!")));
    }

    @Test
    @DisplayName("业务异常返回对应错误码与 HTTP 状态")
    void businessExceptionMapsToErrorCode() throws Exception {
        mockMvc.perform(get("/probe/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("订单不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("参数校验失败返回 400 与字段级说明")
    void validationFailureReturnsFieldDetail() throws Exception {
        mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")));
    }

    @Test
    @DisplayName("未预期异常只返回通用提示，不泄露堆栈和内部信息")
    void unexpectedExceptionDoesNotLeakInternals() throws Exception {
        String body = mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("IllegalStateException")
                .doesNotContain("数据库连接串");
    }

    /**
     * 仅用于契约测试的探针控制器，不参与应用装配。
     */
    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @GetMapping("/ok")
        Result<Map<String, String>> ok() {
            return Result.ok(Map.of("value", "hello"));
        }

        @GetMapping("/business")
        Result<Void> business() {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在");
        }

        @PostMapping("/validation")
        Result<Void> validation(@Valid @RequestBody ProbeRequest request) {
            return Result.ok();
        }

        @GetMapping("/boom")
        Result<Void> boom() {
            throw new IllegalStateException("数据库连接串 jdbc:mysql://secret");
        }
    }

    /**
     * 校验用请求体。
     *
     * @param name 名称
     */
    record ProbeRequest(@NotBlank(message = "不能为空") String name) {
    }
}
