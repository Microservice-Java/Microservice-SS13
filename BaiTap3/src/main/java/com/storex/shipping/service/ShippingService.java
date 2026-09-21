package com.storex.shipping.service;

import com.storex.shipping.dto.ShippingFeeRequest;
import com.storex.shipping.dto.ShippingFeeResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingService {

    private final RestTemplate restTemplate;

    @Value("${shipping-service.url:http://shipping-service/api/v1/shipping/calculate}")
    private String shippingServiceUrl;

    @CircuitBreaker(name = "shippingClient", fallbackMethod = "calculateShippingFeeFallback")
    public ShippingFeeResponse calculateShippingFee(ShippingFeeRequest request) {
        log.info("Calculating shipping fee for orderId={}, weight={}kg, city={}", request.getOrderId(), request.getWeightKg(), request.getDestinationCity());

        ShippingFeeResponse response = restTemplate.postForObject(shippingServiceUrl, request, ShippingFeeResponse.class);

        if (response == null) {
            throw new RestClientException("Null response from Shipping-Service");
        }

        return response;
    }

    /**
     * Phương thức Fallback cho shippingClient CircuitBreaker
     */
    public ShippingFeeResponse calculateShippingFeeFallback(ShippingFeeRequest request, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            log.error("[CIRCUIT BREAKER REJECT] Cầu dao ngắt mạch hoặc vượt định mức trinh sát HALF_OPEN (Max 3 req). Chi tiết: {}", throwable.getMessage());
            return ShippingFeeResponse.builder()
                    .status("FALLBACK_CIRCUIT_OPEN")
                    .shippingFee(30000.0)
                    .provider("DEFAULT_PARTNER")
                    .message("Dịch vụ GHN tạm gián đoạn. Áp dụng phí vận chuyển đồng giá dự phòng 30.000đ.")
                    .build();
        }

        log.error("[SYSTEM ERROR] Gọi Shipping-Service thất bại: {}", throwable.getMessage());
        return ShippingFeeResponse.builder()
                .status("FALLBACK_SYSTEM_ERROR")
                .shippingFee(30000.0)
                .provider("DEFAULT_PARTNER")
                .message("Tạm thời áp dụng phí vận chuyển đồng giá dự phòng 30.000đ.")
                .build();
    }
}
