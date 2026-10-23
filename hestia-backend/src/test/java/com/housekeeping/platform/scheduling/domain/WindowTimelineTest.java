package com.housekeeping.platform.scheduling.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时间窗时间轴纯函数测试。
 *
 * <p>重叠与时长匹配是"窗口能否建立、服务能否放进窗口"的唯一裁决，
 * 边界全部穷举：左闭右开意味着 12:00 结束与 12:00 开始不算重叠，
 * 这个约定一旦含糊，运营就会遇到"界面说重叠了但创建成功"的鬼故事。</p>
 */
class WindowTimelineTest {

    @Nested
    @DisplayName("时间区间合法性")
    class ValidSpan {

        @Test
        @DisplayName("结束晚于开始合法")
        void acceptsOrderedSpan() {
            assertThat(WindowTimeline.validSpan(time("08:00"), time("12:00"))).isTrue();
        }

        @Test
        @DisplayName("结束等于或早于开始非法（暂不支持跨零点）")
        void rejectsReversedOrZeroSpan() {
            assertThat(WindowTimeline.validSpan(time("12:00"), time("12:00"))).isFalse();
            assertThat(WindowTimeline.validSpan(time("18:00"), time("08:00"))).isFalse();
        }

        @Test
        @DisplayName("空值非法")
        void rejectsNull() {
            assertThat(WindowTimeline.validSpan(null, time("12:00"))).isFalse();
            assertThat(WindowTimeline.validSpan(time("08:00"), null)).isFalse();
        }
    }

    @Nested
    @DisplayName("重叠判断（左闭右开）")
    class Overlap {

        @Test
        @DisplayName("首尾相接不算重叠：12:00 结束与 12:00 开始")
        void allowsBackToBack() {
            assertThat(WindowTimeline.overlaps(
                    time("08:00"), time("12:00"), time("12:00"), time("17:00"))).isFalse();
        }

        @Test
        @DisplayName("部分交叠算重叠")
        void detectsPartialOverlap() {
            assertThat(WindowTimeline.overlaps(
                    time("08:00"), time("12:00"), time("11:00"), time("15:00"))).isTrue();
        }

        @Test
        @DisplayName("完全包含算重叠")
        void detectsContainment() {
            assertThat(WindowTimeline.overlaps(
                    time("08:00"), time("21:00"), time("13:00"), time("17:00"))).isTrue();
        }

        @Test
        @DisplayName("相离不算重叠")
        void allowsDisjoint() {
            assertThat(WindowTimeline.overlaps(
                    time("08:00"), time("12:00"), time("13:00"), time("17:00"))).isFalse();
            assertThat(WindowTimeline.overlaps(
                    time("18:00"), time("21:00"), time("08:00"), time("12:00"))).isFalse();
        }

        @Test
        @DisplayName("对称性：A 交 B 与 B 交 A 一致")
        void isSymmetric() {
            assertThat(WindowTimeline.overlaps(
                    time("11:00"), time("15:00"), time("08:00"), time("12:00"))).isTrue();
        }
    }

    @Nested
    @DisplayName("批量互不重叠")
    class AllNonOverlapping {

        @Test
        @DisplayName("早晚三班互不重叠")
        void acceptsStandardWindows() {
            assertThat(WindowTimeline.allNonOverlapping(
                    List.<LocalTime[]>of(span("08:00", "12:00"), span("13:00", "17:00"),
                            span("18:00", "21:00"))))
                    .isTrue();
        }

        @Test
        @DisplayName("任何一对交叠即失败")
        void rejectsAnyOverlap() {
            assertThat(WindowTimeline.allNonOverlapping(
                    List.<LocalTime[]>of(span("08:00", "12:00"), span("13:00", "17:00"),
                            span("11:00", "14:00"))))
                    .isFalse();
        }

        @Test
        @DisplayName("空列表与单窗口视为不重叠")
        void acceptsEmptyAndSingle() {
            assertThat(WindowTimeline.allNonOverlapping(List.<LocalTime[]>of())).isTrue();
            assertThat(WindowTimeline.allNonOverlapping(
                    List.<LocalTime[]>of(span("08:00", "12:00")))).isTrue();
        }
    }

    @Nested
    @DisplayName("服务时长匹配")
    class Fits {

        @Test
        @DisplayName("时长小于窗口长度可放入")
        void acceptsShorterDuration() {
            assertThat(WindowTimeline.fits(240, time("08:00"), time("12:00"))).isTrue();
        }

        @Test
        @DisplayName("时长恰好等于窗口长度可放入（左闭右闭的时长语义）")
        void acceptsExactDuration() {
            assertThat(WindowTimeline.fits(240, time("08:00"), time("12:00"))).isTrue();
        }

        @Test
        @DisplayName("时长超过窗口长度不可放入")
        void rejectsLongerDuration() {
            assertThat(WindowTimeline.fits(241, time("08:00"), time("12:00"))).isFalse();
        }

        @Test
        @DisplayName("非正时长与非法窗口不可放入")
        void rejectsInvalidInput() {
            assertThat(WindowTimeline.fits(0, time("08:00"), time("12:00"))).isFalse();
            assertThat(WindowTimeline.fits(-10, time("08:00"), time("12:00"))).isFalse();
            assertThat(WindowTimeline.fits(60, time("12:00"), time("12:00"))).isFalse();
        }
    }

    @Test
    @DisplayName("窗口长度按分钟计算")
    void computesMinutes() {
        assertThat(WindowTimeline.minutesOf(time("08:00"), time("12:00"))).isEqualTo(240);
        assertThat(WindowTimeline.minutesOf(time("18:00"), time("21:00"))).isEqualTo(180);
    }

    private static LocalTime time(String text) {
        return LocalTime.parse(text);
    }

    private static LocalTime[] span(String start, String end) {
        return new LocalTime[]{time(start), time(end)};
    }
}
