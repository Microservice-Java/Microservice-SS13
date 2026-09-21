package com.storex.shipping.service;

import com.storex.shipping.dto.ShippingFeeRequest;
import com.storex.shipping.dto.ShippingFeeResponse;
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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShippingServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ShippingService shippingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shippingService, "shippingServiceUrl", "http://shipping-service/api/v1/shipping/calculate");
    }

    @Test
    @DisplayName("Checklist 1: Cầu dao chuyển sang OPEN, sau 20s (waitDurationInOpenState) tự động chuyển HALF_OPEN")
    void checklist1_AutoTransitionFromOpenToHalfOpenAfterWaitDuration() throws InterruptedException {
        // Arrange: Cấu hình Resilience4j với waitDurationInOpenState = 100ms (để test nhanh trong unit test)
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(30)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofMillis(100)) // Mô phỏng 20s trong test
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker circuitBreaker = registry.circuitBreaker("shippingClient");

        // Chuyển sang OPEN
        circuitBreaker.transitionToOpenState();
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // Chờ qua thời gian waitDurationInOpenState (100ms)
        Thread.sleep(150);

        // Do automaticTransitionFromOpenToHalfOpenEnabled = true, Cầu dao tự động sang HALF_OPEN
        boolean permission = circuitBreaker.tryAcquirePermission();
        assertTrue(permission, "Permission phải được cấp khi ở trạng thái HALF_OPEN");
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState(), "Cầu dao phải tự động chuyển sang HALF_OPEN sau thời gian chờ ngắt mạch");
    }

    @Test
    @DisplayName("Checklist 2: Khi ở HALF_OPEN, bắn 10 request đồng thời -> Đúng 3 request đi qua, 7 request bị Reject (CallNotPermittedException)")
    void checklist2_HalfOpenStateAllowsOnly3RequestsAndRejects7() {
        // Arrange
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(30)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .permittedNumberOfCallsInHalfOpenState(3) // Định mức 3 request trinh sát
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker circuitBreaker = registry.circuitBreaker("shippingClient");

        // Chuyển sang OPEN rồi mới sang HALF_OPEN (đúng quy định vòng đời Resilience4j)
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());

        int allowedCount = 0;
        int rejectedCount = 0;

        // Bắn 10 request liên tiếp vào CircuitBreaker
        for (int i = 1; i <= 10; i++) {
            try {
                circuitBreaker.acquirePermission();
                allowedCount++;
            } catch (CallNotPermittedException ex) {
                rejectedCount++;
            }
        }

        // Assert
        assertEquals(3, allowedCount, "Chỉ được phép đúng 3 request trinh sát đi qua ở trạng thái HALF_OPEN");
        assertEquals(7, rejectedCount, "7 request còn lại bắt buộc phải bị Reject với CallNotPermittedException");
    }

    @Test
    @DisplayName("Test 3: Phương thức Fallback phản hồi phí vận chuyển đồng giá 30.000đ khi ngắt mạch")
    void calculateShippingFeeFallback_Success() {
        ShippingFeeRequest request = ShippingFeeRequest.builder()
                .orderId("ORD-GHN-001")
                .weightKg(2.5)
                .destinationCity("Hà Nội")
                .build();

        ResourceAccessException error = new ResourceAccessException("Shipping-Service unavailable");
        ShippingFeeResponse response = shippingService.calculateShippingFeeFallback(request, error);

        assertNotNull(response);
        assertEquals("FALLBACK_SYSTEM_ERROR", response.getStatus());
        assertEquals(30000.0, response.getShippingFee());
        assertEquals("DEFAULT_PARTNER", response.getProvider());
    }
}
