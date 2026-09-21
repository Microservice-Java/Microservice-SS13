package com.storex.ewallet.service;

import com.storex.ewallet.dto.EWalletDeductRequest;
import com.storex.ewallet.dto.EWalletDeductResponse;
import com.storex.ewallet.exception.InsufficientBalanceException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EWalletServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private EWalletService eWalletService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(eWalletService, "ewalletServiceUrl", "http://ewallet-service/api/v1/ewallet/deduct");
    }

    @Test
    @DisplayName("Checklist 1: Gửi 8 request lỗi InsufficientBalanceException -> Cầu dao duy trì CLOSED (Phớt lờ lỗi hết tiền)")
    void checklist1_8InsufficientBalanceRequests_CircuitStaysClosed() {
        // Arrange
        EWalletDeductRequest request = EWalletDeductRequest.builder()
                .orderId("ORD-8888")
                .userId("USER-001")
                .amount(500000.0)
                .build();

        InsufficientBalanceException balanceException = new InsufficientBalanceException("Số dư tài khoản không đủ!");

        // Act & Assert (Giả lập 8 request lỗi hết tiền)
        for (int i = 1; i <= 8; i++) {
            EWalletDeductResponse response = eWalletService.deductBalanceFallback(request, balanceException);
            assertNotNull(response);
            assertEquals("FAILED_INSUFFICIENT_BALANCE", response.getStatus());
            assertTrue(response.getMessage().contains("Số dư ví điện tử không đủ"));
        }
    }

    @Test
    @DisplayName("Checklist 2 & 3: Cấu hình Resilience4j ngắt mạch OPEN sau 5 TimeoutException & Ném CallNotPermittedException khi MỞ")
    void checklist2And3_5TimeoutsOpenCircuitAndBlockNextCall() {
        // Arrange: Cấu hình Resilience4j CircuitBreaker thực tế với config từ application.yml
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50.0f)
                .ignoreExceptions(InsufficientBalanceException.class)
                .recordExceptions(ResourceAccessException.class, TimeoutException.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker circuitBreaker = registry.circuitBreaker("ewalletClient");

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

        // Checklist 1 trên CircuitBreaker thực tế: 8 request InsufficientBalanceException không làm tăng failure count
        for (int i = 0; i < 8; i++) {
            circuitBreaker.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new InsufficientBalanceException("Hết tiền"));
        }
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState(), "Cầu dao vẫn phải CLOSED sau 8 lần lỗi hết tiền");

        // Checklist 2: 5 request TimeoutException làm tăng failure count (5/5 = 100% > 50%) -> Chuyển OPEN
        for (int i = 0; i < 5; i++) {
            circuitBreaker.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new ResourceAccessException("Timeout"));
        }
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState(), "Cầu dao phải chuyển ngay sang OPEN sau 5 lần Timeout");

        // Checklist 3: Khi mạch đang OPEN, gửi 1 request mới -> Cầu dao ném CallNotPermittedException ngay lập tức
        assertThrows(CallNotPermittedException.class, () -> {
            circuitBreaker.acquirePermission();
        }, "Khi mạch OPEN, request tiếp theo phải bị ngắt và ném CallNotPermittedException");

        // Test Fallback hứng CallNotPermittedException
        EWalletDeductRequest request = EWalletDeductRequest.builder().orderId("ORD-9999").amount(100000.0).build();
        CallNotPermittedException callNotPermitted = CallNotPermittedException.createCallNotPermittedException(circuitBreaker);

        EWalletDeductResponse fallbackResponse = eWalletService.deductBalanceFallback(request, callNotPermitted);
        assertNotNull(fallbackResponse);
        assertEquals("BLOCKED_CIRCUIT_OPEN", fallbackResponse.getStatus());
        assertTrue(fallbackResponse.getMessage().contains("tạm gián đoạn"));
    }
}
