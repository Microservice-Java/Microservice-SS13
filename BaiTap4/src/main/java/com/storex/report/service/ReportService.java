package com.storex.report.service;

import com.storex.report.dto.ReportResponse;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class ReportService {

    /**
     * Phương thức xuất báo cáo Excel nặng
     * Áp dụng RateLimiter (tối đa 2 req / 1 phút / user) và Bulkhead (tối đa 5 luồng đồng thời)
     */
    @RateLimiter(name = "reportRateLimiter", fallbackMethod = "rateLimiterFallback")
    @Bulkhead(name = "reportBulkhead", fallbackMethod = "bulkheadFallback")
    public ReportResponse generateExcelReport(String userId, String reportType) {
        log.info("Generating Excel report: userId={}, reportType={}", userId, reportType);

        try {
            // Giả lập thời gian xuất Excel nặng (chiếm luồng)
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String fileId = UUID.randomUUID().toString().substring(0, 8);
        return ReportResponse.builder()
                .status("SUCCESS")
                .downloadUrl("https://cdn.storex.vn/reports/excel_" + reportType + "_" + fileId + ".xlsx")
                .message("Tạo báo cáo Excel thành công!")
                .httpStatusCode(200)
                .build();
    }

    /**
     * Fallback khi vi phạm RateLimiter (gọi 3 lần / phút -> HTTP 429)
     */
    public ReportResponse rateLimiterFallback(String userId, String reportType, RequestNotPermitted ex) {
        log.warn("[RATE LIMIT EXCEEDED] User {} thao tác vượt định mức (Tối đa 2 req / phút). Chi tiết: {}", userId, ex.getMessage());
        return ReportResponse.builder()
                .status("TOO_MANY_REQUESTS")
                .downloadUrl(null)
                .message("Bạn thao tác quá nhanh, vui lòng thử lại sau!")
                .httpStatusCode(429)
                .build();
    }

    /**
     * Fallback khi vi phạm Bulkhead (quá 5 luồng đồng thời -> HTTP 503)
     */
    public ReportResponse bulkheadFallback(String userId, String reportType, BulkheadFullException ex) {
        log.error("[BULKHEAD FULL] Server quá tải luồng kết xuất Excel đồng thời (>5 threads). Chi tiết: {}", ex.getMessage());
        return ReportResponse.builder()
                .status("SERVICE_UNAVAILABLE")
                .downloadUrl(null)
                .message("Server đang quá tải quá trình kết xuất!")
                .httpStatusCode(503)
                .build();
    }

    /**
     * Fallback chung cho cả 2 trường hợp ngoại lệ Throwable
     */
    public ReportResponse generalFallback(String userId, String reportType, Throwable throwable) {
        if (throwable instanceof RequestNotPermitted) {
            return rateLimiterFallback(userId, reportType, (RequestNotPermitted) throwable);
        } else if (throwable instanceof BulkheadFullException) {
            return bulkheadFallback(userId, reportType, (BulkheadFullException) throwable);
        }

        log.error("[UNKNOWN REPORT ERROR] Lỗi không xác định khi xuất báo cáo: {}", throwable.getMessage(), throwable);
        return ReportResponse.builder()
                .status("INTERNAL_SERVER_ERROR")
                .downloadUrl(null)
                .message("Đã xảy ra lỗi khi tạo báo cáo!")
                .httpStatusCode(500)
                .build();
    }
}
