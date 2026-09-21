# BÁO CÁO BÀI TẬP THỰC HÀNH 1 (SS13)
**TRIỂN KHAI FALLBACK PATTERN BẢO VỆ GIAO DIỆN TRANG CHỦ**

---

## I. TỰ ĐÁNH GIÁ THEO CHECKLIST BÀI TẬP

### 1. Hàm Fallback có cùng kiểu trả về và cùng tham số với hàm gốc không?
- **ĐÃ ĐẠT (PASS)**.
- Phương thức gốc: `public List<FlashVoucherDto> getFlashVouchers()`
- Phương thức Fallback: `public List<FlashVoucherDto> getFlashVouchersFallback(Throwable throwable)`
- **Đánh giá**: Cả 2 hàm đều có cùng kiểu trả về `List<FlashVoucherDto>`, giữ nguyên danh sách tham số đầu vào của hàm gốc (0 tham số).

### 2. Tham số cuối cùng của hàm Fallback có phải là Throwable không?
- **ĐÃ ĐẠT (PASS)**.
- Khai báo: `public List<FlashVoucherDto> getFlashVouchersFallback(Throwable throwable)`
- **Đánh giá**: Resilience4j sẽ tự động inject đối tượng `Throwable` (chứa chi tiết lỗi mạng `RestClientException` hoặc lỗi ngắt mạch `CallNotPermittedException`) vào tham số cuối cùng này. Hàm đã thực hiện log mức `ERROR` chi tiết cho quản trị viên theo dõi.

### 3. Khi tắt mạng hoặc gọi sai URL, Frontend có nhận được mã `DEFAULT_FREESHIP` với HTTP Status 200 OK không?
- **ĐÃ ĐẠT (PASS)**.
- **Kịch bản**: Giả lập gọi tới URL `http://localhost:9999/api/v1/vouchers/flash` (dịch vụ không tồn tại).
- **Kết quả**: `VoucherController` vẫn phản hồi về cho Frontend với mã trạng thái **HTTP 200 OK** và JSON body chính xác theo định dạng yêu cầu:
  ```json
  [
    {
      "voucher_code": "DEFAULT_FREESHIP",
      "discount_amount": 15000.0,
      "description": "Freeship cho mọi đơn hàng (Hệ thống dự phòng)"
    }
  ]
  ```

---

## II. CHI TIẾT MÃ NGUỒN TRIỂN KHAI

### 1. DTO Phản Hồi (`FlashVoucherDto.java`)
Sử dụng `@JsonProperty` để map chính xác tên thuộc tính theo dạng `snake_case` như thiết kế yêu cầu:

```java
package com.storex.bff.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashVoucherDto {

    @JsonProperty("voucher_code")
    private String voucherCode;

    @JsonProperty("discount_amount")
    private Double discountAmount;

    @JsonProperty("description")
    private String description;
}
```

### 2. Service & Fallback Logic (`VoucherService.java`)

```java
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
        log.info("Calling Marketing-Service at: {}", marketingServiceUrl);

        ResponseEntity<List<FlashVoucherDto>> response = restTemplate.exchange(
                marketingServiceUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<FlashVoucherDto>>() {}
        );

        return response.getBody() != null ? response.getBody() : List.of();
    }

    public List<FlashVoucherDto> getFlashVouchersFallback(Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            log.error("[CIRCUIT BREAKER OPEN] Cầu dao voucherCircuitBreaker Mở mạch! Chi tiết: {}", throwable.getMessage());
        } else if (throwable instanceof RestClientException) {
            log.error("[MARKETING SERVICE ERROR] Gọi Marketing-Service thất bại: {}", throwable.getMessage());
        } else {
            log.error("[UNKNOWN ERROR] Sự cố không xác định khi lấy Flash Vouchers: {}", throwable.getMessage(), throwable);
        }

        log.info("[FALLBACK] Tự động trả về Voucher mặc định DEFAULT_FREESHIP để bảo vệ trang chủ (HTTP 200 OK).");

        FlashVoucherDto defaultVoucher = FlashVoucherDto.builder()
                .voucherCode("DEFAULT_FREESHIP")
                .discountAmount(15000.0)
                .description("Freeship cho mọi đơn hàng (Hệ thống dự phòng)")
                .build();

        return List.of(defaultVoucher);
    }
}
```

### 3. File Cấu Hình (`application.yml`)

```yaml
server:
  port: 8080

spring:
  application:
    name: storex-bff

resilience4j:
  circuitbreaker:
    instances:
      voucherCircuitBreaker:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 5
        minimumNumberOfCalls: 3
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 2
        automaticTransitionFromOpenToHalfOpenEnabled: true

marketing-service:
  url: http://localhost:9999/api/v1/vouchers/flash
```

---

## III. KẾT QUẢ KIỂM THỬ (UNIT TEST)

Bộ unit test trong [VoucherServiceTest.java](file:///d:/microservice/BaiTap/SS13/BaiTap1/src/test/java/com/storex/bff/service/VoucherServiceTest.java) đã kiểm chứng 100% PASS:
- `getFlashVouchers_Success`: Trả về danh sách voucher khi Marketing-Service bình thường.
- `getFlashVouchersFallback_NetworkError`: Tự động ngắt và trả về `DEFAULT_FREESHIP` 15K khi lỗi mạng/URL sai.
- `getFlashVouchersFallback_CircuitBreakerOpen`: Tự động trả về `DEFAULT_FREESHIP` khi Cầu dao Mở mạch.

```text
BUILD SUCCESSFUL in 35s
4 actionable tasks: 4 executed
```
