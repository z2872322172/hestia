package com.housekeeping.platform.operations.domain;

import com.housekeeping.platform.booking.persistence.query.BookingExportRow;

import java.util.List;

/**
 * 订单导出 CSV 生成（docs/modules/13 第 11 节）。纯函数。
 *
 * <p>导出列只包含报表必需字段；联系人姓名 / 电话 / 详址等隐私字段
 * 不进入导出文件。含逗号、引号或换行的字段按 RFC 4180 加引号转义。</p>
 */
public final class OrderExportCsv {

    private static final String HEADER = "order_no,service_area_id,service_name,order_status,"
            + "payment_status,appointment_date,payable_amount,paid_amount,refunded_amount,created_at";

    private OrderExportCsv() {
    }

    /**
     * 生成 CSV 文本（UTF-8，不含 BOM，行分隔符 \n）。
     *
     * @param rows 导出行
     * @return CSV 文本
     */
    public static String assemble(List<BookingExportRow> rows) {
        StringBuilder csv = new StringBuilder(HEADER);
        for (BookingExportRow row : rows == null ? List.<BookingExportRow>of() : rows) {
            csv.append('\n')
                    .append(escape(row.getOrderNo())).append(',')
                    .append(row.getServiceAreaId() == null ? "" : row.getServiceAreaId()).append(',')
                    .append(escape(row.getServiceName())).append(',')
                    .append(escape(row.getOrderStatus())).append(',')
                    .append(escape(row.getPaymentStatus())).append(',')
                    .append(row.getAppointmentDate() == null ? "" : row.getAppointmentDate()).append(',')
                    .append(row.getPayableAmount() == null ? "" : row.getPayableAmount()).append(',')
                    .append(row.getPaidAmount() == null ? "" : row.getPaidAmount()).append(',')
                    .append(row.getRefundedAmount() == null ? "" : row.getRefundedAmount()).append(',')
                    .append(row.getCreatedAt() == null ? "" : row.getCreatedAt());
        }
        return csv.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
