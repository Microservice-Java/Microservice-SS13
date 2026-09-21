# BÁO CÁO BÀI TẬP THỰC HÀNH 2 (SS13)
**CẤU HÌNH CỬA SỐ TRƯỢT COUNT-BASED VÀ BẪY NGOẠI LỆ NGHIỆP VỤ**

---

## I. TỰ ĐÁNH GIÁ THEO CHECKLIST BÀI TẬP

### 1. Gửi liên tiếp 8 request ném lỗi `InsufficientBalanceException`. Cầu dao có đóng (CLOSED) bình thường không?
- **ĐÃ ĐẠT (CLOSED)**.
- **Giải thích**: Do `InsufficientBalanceException` (Lỗi người dùng - HTTP 400) đã được cấu hình trong danh sách `ignoreExceptions`, Resilience4j **bỏ qua không đếm** các ngoại lệ này vào bộ đếm tỷ lệ lỗi (`failureRateThreshold`). Trạng thái Cầu dao giữ nguyên `CLOSED`, giúp các khách hàng khác có đủ tiền vẫn thực hiện thanh toán bình thường.

### 2. Gửi liên tiếp 5 request ném lỗi `TimeoutException`. Cầu dao có lập tức MỞ (OPEN) không?
- **ĐÃ ĐẠT (OPEN)**.
- **Giải thích**: `TimeoutException` (Lỗi hệ thống - HTTP 504) nằm trong danh sách `recordExceptions`. Khi gửi 5 request lỗi liên tiếp, số lượng cuộc gọi đã đạt ngưỡng tối thiểu `minimumNumberOfCalls = 5`, tỷ lệ lỗi đạt $5/5 = 100\% > 50\%$. Resilience4j lập tức chuyển trạng thái Cầu dao sang `OPEN` để ngắt mạch bảo vệ hệ thống trong 10 giây.

### 3. Khi mạch đang OPEN, gửi 1 request hợp lệ, hệ thống có ném ra lỗi `CallNotPermittedException` ngay lập tức không?
- **ĐÃ ĐẠT (CallNotPermittedException)**.
- **Giải thích**: Khi trạng thái Cầu dao là `OPEN`, Resilience4j sẽ chặn hoàn toàn các request đi qua (short-circuit) và ném ra ngoại lệ `CallNotPermittedException` ngay lập tức mà không gọi tới `EWallet-Service`. Phương thức Fallback bắt ngoại lệ này, log thông báo `[CIRCUIT BREAKER OPEN]` và phản hồi kết quả gián đoạn tạm thời cho client.

---

## II. CHI TIẾT FILE CẤU HÌNH APPLICATION.YML

```yaml
server:
  port: 8085

spring:
  application:
    name: checkout-ewallet-service

resilience4j:
  circuitbreaker:
    instances:
      ewalletClient:
        # Cửa sổ trượt dạng số lượng request (COUNT_BASED)
        slidingWindowType: COUNT_BASED
        # Kích thước cửa sổ trượt (10 request gần nhất)
        slidingWindowSize: 10
        # Số cuộc gọi tối thiểu để bắt đầu tính toán (5 request)
        minimumNumberOfCalls: 5
        # Ngưỡng tỷ lệ lỗi ngắt mạch (50%)
        failureRateThreshold: 50
        # Thời gian ngắt mạch khi OPEN (10 giây)
        waitDurationInOpenState: 10s
        # Số lượng request trinh sát khi HALF_OPEN
        permittedNumberOfCallsInHalfOpenState: 3

        # Lỗi nghiệp vụ bị phớt lờ (Khách hàng không đủ tiền -> Giữ Cầu dao CLOSED)
        ignoreExceptions:
          - com.storex.ewallet.exception.InsufficientBalanceException

        # Lỗi hệ thống ghi nhận tính tỷ lệ lỗi (Treo mạng / Timeout -> Mở mạch OPEN)
        recordExceptions:
          - java.util.concurrent.TimeoutException
          - org.springframework.web.client.ResourceAccessException
          - java.net.ConnectException

ewallet-service:
  url: http://ewallet-service/api/v1/ewallet/deduct
```

---

## III. MÃ NGUỒN XỬ LÝ FALLBACK (`EWalletService.java`)

```java
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
```

---

## IV. KẾT QUẢ KIỂM THỬ (UNIT TEST)

Bộ kiểm thử [EWalletServiceTest.java](file:///d:/microservice/BaiTap/SS13/BaiTap2/src/test/java/com/storex/ewallet/service/EWalletServiceTest.java):
```text
BUILD SUCCESSFUL in 23s
4 actionable tasks: 4 executed
```
- Checklist 1: 8 request `InsufficientBalanceException` $\rightarrow$ Cầu dao giữ `CLOSED`.
- Checklist 2: 5 request `TimeoutException` $\rightarrow$ Cầu dao chuyển `OPEN`.
- Checklist 3: Request khi `OPEN` $\rightarrow$ Ném `CallNotPermittedException` lập tức.
