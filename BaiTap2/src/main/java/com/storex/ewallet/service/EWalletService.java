package com.storex.ewallet.service;

import com.storex.ewallet.dto.EWalletDeductRequest;
import com.storex.ewallet.dto.EWalletDeductResponse;
import com.storex.ewallet.exception.InsufficientBalanceException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EWalletService {

    private final RestTemplate restTemplate;

    @Value("${ewallet-service.url:http://ewallet-service/api/v1/ewallet/deduct}")
    private String ewalletServiceUrl;

    @CircuitBreaker(name = "ewalletClient", fallbackMethod = "deductBalanceFallback")
    public EWalletDeductResponse deductBalance(EWalletDeductRequest request) {
        log.info("Deducting balance for orderId={}, userId={}, amount={}", request.getOrderId(), request.getUserId(), request.getAmount());

        EWalletDeductResponse response = restTemplate.postForObject(ewalletServiceUrl, request, EWalletDeductResponse.class);

        if (response == null) {
            throw new RestClientException("Null response from EWallet-Service");
        }

        return response;
    }

    /**
     * Phương thức Fallback cho ewalletClient CircuitBreaker
     */
    public EWalletDeductResponse deductBalanceFallback(EWalletDeductRequest request, Throwable throwable) {
        if (throwable instanceof InsufficientBalanceException) {
            log.info("[BUSINESS EXCEPTION IGNORED] Khách hàng hết tiền trong ví. Ngoại lệ bị phớt lờ, Cầu dao giữ CLOSED: {}", throwable.getMessage());
            return EWalletDeductResponse.builder()
                    .status("FAILED_INSUFFICIENT_BALANCE")
                    .transactionId(null)
                    .message("Số dư ví điện tử không đủ để thực hiện thanh toán!")
                    .build();
        }

        if (throwable instanceof CallNotPermittedException) {
            log.error("[CIRCUIT BREAKER OPEN] Cầu dao ewalletClient Mở mạch! Ném CallNotPermittedException ngắt kết nối. Chi tiết: {}", throwable.getMessage());
            return EWalletDeductResponse.builder()
                    .status("BLOCKED_CIRCUIT_OPEN")
                    .transactionId(null)
                    .message("Hệ thống thanh toán Ví điện tử tạm gián đoạn. Vui lòng thử lại sau!")
                    .build();
        }

        log.error("[SYSTEM TIMEOUT ERROR] Lỗi hệ thống treo mạng kết nối tới EWallet-Service: {}", throwable.getMessage());
        return EWalletDeductResponse.builder()
                .status("FAILED_TIMEOUT")
                .transactionId(null)
                .message("Kết nối Ví điện tử bị timeout. Vui lòng thử lại sau!")
                .build();
    }
}
