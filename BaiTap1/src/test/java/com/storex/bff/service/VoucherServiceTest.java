package com.storex.bff.service;

import com.storex.bff.dto.FlashVoucherDto;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private VoucherService voucherService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(voucherService, "marketingServiceUrl", "http://localhost:9999/api/v1/vouchers/flash");
    }

    @Test
    @DisplayName("Test 1: Lấy danh sách Flash Voucher thành công khi Marketing-Service bình thường")
    void getFlashVouchers_Success() {
        // Arrange
        List<FlashVoucherDto> mockVouchers = List.of(
                new FlashVoucherDto("FLASH50", 50000.0, "Giảm 50K khung giờ vàng"),
                new FlashVoucherDto("FLASH100", 100000.0, "Giảm 100K đơn từ 1 triệu")
        );

        when(restTemplate.exchange(
                eq("http://localhost:9999/api/v1/vouchers/flash"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(mockVouchers, HttpStatus.OK));

        // Act
        List<FlashVoucherDto> result = voucherService.getFlashVouchers();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("FLASH50", result.get(0).getVoucherCode());
        assertEquals(50000.0, result.get(0).getDiscountAmount());

        verify(restTemplate, times(1)).exchange(
                eq("http://localhost:9999/api/v1/vouchers/flash"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("Test 2: Fallback tự động trả về DEFAULT_FREESHIP (15K) khi Marketing-Service bị lỗi/offline")
    void getFlashVouchersFallback_NetworkError() {
        // Arrange
        ResourceAccessException networkError = new ResourceAccessException("Connection refused: connect to localhost:9999");

        // Act
        List<FlashVoucherDto> fallbackResult = voucherService.getFlashVouchersFallback(networkError);

        // Assert
        assertNotNull(fallbackResult);
        assertEquals(1, fallbackResult.size());

        FlashVoucherDto voucher = fallbackResult.get(0);
        assertEquals("DEFAULT_FREESHIP", voucher.getVoucherCode());
        assertEquals(15000.0, voucher.getDiscountAmount());
        assertEquals("Freeship cho mọi đơn hàng (Hệ thống dự phòng)", voucher.getDescription());
    }

    @Test
    @DisplayName("Test 3: Fallback tự động khi Cầu dao đang Mở mạch (CallNotPermittedException)")
    void getFlashVouchersFallback_CircuitBreakerOpen() {
        // Arrange
        CircuitBreaker mockCircuitBreaker = mock(CircuitBreaker.class);
        CircuitBreakerConfig mockConfig = mock(CircuitBreakerConfig.class);
        when(mockCircuitBreaker.getCircuitBreakerConfig()).thenReturn(mockConfig);
        when(mockConfig.isWritableStackTraceEnabled()).thenReturn(true);

        CallNotPermittedException circuitOpenException = CallNotPermittedException.createCallNotPermittedException(mockCircuitBreaker);

        // Act
        List<FlashVoucherDto> fallbackResult = voucherService.getFlashVouchersFallback(circuitOpenException);

        // Assert
        assertNotNull(fallbackResult);
        assertEquals(1, fallbackResult.size());

        FlashVoucherDto voucher = fallbackResult.get(0);
        assertEquals("DEFAULT_FREESHIP", voucher.getVoucherCode());
        assertEquals(15000.0, voucher.getDiscountAmount());
    }
}
