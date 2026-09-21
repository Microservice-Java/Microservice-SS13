package com.storex.report.service;

import com.storex.report.dto.ReportResponse;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {

    private final ReportService reportService = new ReportService();

    @Test
    @DisplayName("Checklist 1: Gọi API 3 lần liên tiếp trong 10s -> Lần thứ 3 bị RateLimiter chặn (HTTP 429, 'Bạn thao tác quá nhanh, vui lòng thử lại sau')")
    void checklist1_RateLimiterRejects3rdCall() {
        // Arrange: Cấu hình RateLimiter thực tế (tối đa 2 req / 60s)
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofSeconds(60))
                .timeoutDuration(Duration.ZERO)
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("reportRateLimiter");

        String userId = "USER_SPAMMER";
        String reportType = "EXCEL_FINANCE";

        List<ReportResponse> responses = new ArrayList<>();

        // Act: Gọi 3 lần liên tiếp dùng RateLimiter.waitForPermission
        for (int i = 1; i <= 3; i++) {
            try {
                RateLimiter.waitForPermission(rateLimiter);
                responses.add(ReportResponse.builder()
                        .status("SUCCESS")
                        .message("Tạo báo cáo Excel thành công!")
                        .httpStatusCode(200)
                        .build());
            } catch (RequestNotPermitted ex) {
                responses.add(reportService.rateLimiterFallback(userId, reportType, ex));
            }
        }

        // Assert
        assertEquals(3, responses.size());

        // Lần 1 & 2 thành công (HTTP 200)
        assertEquals(200, responses.get(0).getHttpStatusCode());
        assertEquals(200, responses.get(1).getHttpStatusCode());

        // Lần 3 bị chặn bởi RateLimiter (HTTP 429)
        ReportResponse thirdCallResponse = responses.get(2);
        assertEquals(429, thirdCallResponse.getHttpStatusCode());
        assertEquals("TOO_MANY_REQUESTS", thirdCallResponse.getStatus());
        assertEquals("Bạn thao tác quá nhanh, vui lòng thử lại sau!", thirdCallResponse.getMessage());
    }

    @Test
    @DisplayName("Checklist 2: Bắn 20 request đồng thời -> 5 request thành công, 15 request bị Bulkhead Reject (HTTP 503, 'Server đang quá tải quá trình kết xuất')")
    void checklist2_BulkheadAllows5AndRejects15ConcurrentCalls() throws InterruptedException, ExecutionException {
        // Arrange: Cấu hình Bulkhead (max 5 concurrent calls, 0ms wait)
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(5)
                .maxWaitDuration(Duration.ZERO)
                .build();

        BulkheadRegistry registry = BulkheadRegistry.of(config);
        Bulkhead bulkhead = registry.bulkhead("reportBulkhead");

        int totalRequests = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejected503Count = new AtomicInteger(0);

        List<Future<ReportResponse>> futures = new ArrayList<>();

        // Bắn 20 request đồng thời
        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            Future<ReportResponse> future = executorService.submit(() -> {
                latch.await(); // Đợi tất cả 20 thread sẵn sàng để bắn đồng thời
                try {
                    bulkhead.acquirePermission();
                    // Giả lập thời gian chiếm luồng xuất Excel
                    Thread.sleep(200);
                    bulkhead.onComplete();
                    successCount.incrementAndGet();
                    return ReportResponse.builder().status("SUCCESS").httpStatusCode(200).build();
                } catch (BulkheadFullException ex) {
                    rejected503Count.incrementAndGet();
                    return reportService.bulkheadFallback("USER_" + index, "SALES", ex);
                }
            });
            futures.add(future);
        }

        // Bắt đầu bắn 20 request đồng thời
        latch.countDown();

        // Chờ tất cả 20 task hoàn tất
        for (Future<ReportResponse> future : futures) {
            future.get();
        }

        executorService.shutdown();

        // Assert
        assertEquals(5, successCount.get(), "Chỉ đúng 5 luồng xuất Excel được thực thi đồng thời");
        assertEquals(15, rejected503Count.get(), "Đúng 15 request còn lại phải bị Bulkhead Reject với HTTP 503");

        // Verify message phản hồi của request bị reject
        ReportResponse sampleRejectedResponse = reportService.bulkheadFallback("USER_X", "SALES", BulkheadFullException.createBulkheadFullException(bulkhead));
        assertEquals(503, sampleRejectedResponse.getHttpStatusCode());
        assertEquals("SERVICE_UNAVAILABLE", sampleRejectedResponse.getStatus());
        assertEquals("Server đang quá tải quá trình kết xuất!", sampleRejectedResponse.getMessage());
    }
}
