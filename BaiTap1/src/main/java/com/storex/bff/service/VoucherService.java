package com.storex.bff.service;

import com.storex.bff.dto.FlashVoucherDto;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherService {

    private final RestTemplate restTemplate;

    @Value("${marketing-service.url:http://localhost:9999/api/v1/vouchers/flash}")
    private String marketingServiceUrl;

    @CircuitBreaker(name = "voucherCircuitBreaker", fallbackMethod = "getFlashVouchersFallback")
    public List<FlashVoucherDto> getFlashVouchers() {
        log.info("Calling Marketing-Service to fetch flash vouchers at: {}", marketingServiceUrl);

        ResponseEntity<List<FlashVoucherDto>> response = restTemplate.exchange(
                marketingServiceUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<FlashVoucherDto>>() {}
        );

        return response.getBody() != null ? response.getBody() : List.of();
    }

    /**
     * Phương thức Fallback bảo vệ giao diện trang chủ khi Marketing-Service bị sự cố/bảo trì
     * 
     * Checklist Quy tắc Chữ ký (Signature Rules):
     * 1. Cùng kiểu trả về: List<FlashVoucherDto> (khớp với phương thức gốc getFlashVouchers)
     * 2. Cùng tham số với hàm gốc (không có tham số)
     * 3. Tham số cuối cùng bắt buộc là Throwable throwable để in log nguyên nhân
     */
    public List<FlashVoucherDto> getFlashVouchersFallback(Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            log.error("[CIRCUIT BREAKER OPEN] Cầu dao voucherCircuitBreaker đang Mở mạch! Tạm ngắt kết nối tới Marketing-Service. Chi tiết: {}", throwable.getMessage());
        } else if (throwable instanceof RestClientException) {
            log.error("[MARKETING SERVICE ERROR] Không thể gọi tới Marketing-Service (Offline/URL sai). Chi tiết: {}", throwable.getMessage());
        } else {
            log.error("[UNKNOWN ERROR] Sự cố không xác định khi lấy Flash Vouchers: {}", throwable.getMessage(), throwable);
        }

        log.info("[FALLBACK TRIGGERED] Tự động trả về Voucher mặc định DEFAULT_FREESHIP để bảo vệ trang chủ (HTTP 200 OK).");

        FlashVoucherDto defaultVoucher = FlashVoucherDto.builder()
                .voucherCode("DEFAULT_FREESHIP")
                .discountAmount(15000.0)
                .description("Freeship cho mọi đơn hàng (Hệ thống dự phòng)")
                .build();

        return List.of(defaultVoucher);
    }
}
