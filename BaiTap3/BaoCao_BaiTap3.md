# BÁO CÁO BÀI TẬP THỰC HÀNH 3 (SS13)
**THỬ LỬA VỚI TRẠNG THÁI HALF-OPEN VÀ TIME-BASED**

---

## I. THÔNG SỐ CẤU HÌNH INPUT CONFIG KỲ VỌNG (SLA AGREEMENT)

| Thông Số Cấu Hình | Giá Trị Thiết Lập | Tương Ứng Thuộc Tính Resilience4j |
| :--- | :---: | :--- |
| **Loại cửa sổ** | `TIME_BASED` | `slidingWindowType: TIME_BASED` |
| **Kích thước cửa sổ** | `30 giây` | `slidingWindowSize: 30` |
| **Thời gian chờ ngắt mạch (Open $\rightarrow$ Half-Open)** | `20 giây` | `waitDurationInOpenState: 20s` |
| **Số request thử nghiệm (Half-Open)** | `3 request` | `permittedNumberOfCallsInHalfOpenState: 3` |
| **Tự động chuyển Half-Open** | `true` | `automaticTransitionFromOpenToHalfOpenEnabled: true` |

---

## II. ĐÁNH GIÁ THEO CHECKLIST BÀI TẬP

### 1. Sau khi ép mạch chuyển sang OPEN, bạn đợi đủ 20 giây. Trạng thái có tự động chuyển sang HALF_OPEN không?
- **ĐÃ ĐẠT (HALF_OPEN)**.
- **Giải thích**: Nhờ bật tính năng `automaticTransitionFromOpenToHalfOpenEnabled = true`, ngay khi thời gian chờ `waitDurationInOpenState = 20s` kết thúc, Resilience4j tự động hé cửa chuyển trạng thái từ `OPEN` sang `HALF_OPEN` mà không cần phải chờ có request mồi từ phía client.

### 2. Khi đang ở HALF_OPEN, nếu bắn cùng lúc 10 request, hệ thống có chỉ cho phép đúng 3 request đi qua, 7 request còn lại bị Reject (CallNotPermittedException) không?
- **ĐÃ ĐẠT (3 Allowed, 7 Rejected)**.
- **Giải thích**: Do đã quy định định mức `permittedNumberOfCallsInHalfOpenState = 3`, tại trạng thái `HALF_OPEN`, Resilience4j sẽ chỉ cho phép đúng 3 request thử nghiệm đi qua sang `Shipping-Service`. 7 request còn lại bị từ chối ngắt mạch lập tức với ngoại lệ `CallNotPermittedException`.

---

## III. FILE CẤU HÌNH APPLICATION.YML HOÀN CHỈNH

```yaml
server:
  port: 8086

spring:
  application:
    name: checkout-shipping-service

resilience4j:
  circuitbreaker:
    instances:
      shippingClient:
        # Cửa sổ trượt theo thời gian (TIME_BASED)
        slidingWindowType: TIME_BASED
        # Kích thước cửa sổ trượt (30 giây)
        slidingWindowSize: 30
        # Số cuộc gọi tối thiểu để tính toán tỷ lệ lỗi (3 request)
        minimumNumberOfCalls: 3
        # Ngưỡng tỷ lệ lỗi ngắt mạch (50%)
        failureRateThreshold: 50
        # Thời gian ngưng gọi khi sập (Open to Half-Open: 20 giây theo SLA)
        waitDurationInOpenState: 20s
        # Số lượng request trinh sát cho phép ở trạng thái HALF_OPEN (3 request theo SLA)
        permittedNumberOfCallsInHalfOpenState: 3
        # Tự động hé cửa chuyển sang HALF_OPEN khi hết 20s mà không cần request mồi
        automaticTransitionFromOpenToHalfOpenEnabled: true

shipping-service:
  url: http://shipping-service/api/v1/shipping/calculate
```

---

## IV. MÃ NGUỒN XỬ LÝ FALLBACK (`ShippingService.java`)

```java
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
        log.info("Calculating shipping fee for orderId={}, weight={}kg", request.getOrderId(), request.getWeightKg());

        ShippingFeeResponse response = restTemplate.postForObject(shippingServiceUrl, request, ShippingFeeResponse.class);

        if (response == null) {
            throw new RestClientException("Null response from Shipping-Service");
        }

        return response;
    }

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
```

---

## V. KẾT QUẢ KIỂM THỬ (UNIT & SLA TEST)

Bộ kiểm thử [ShippingServiceTest.java](file:///d:/microservice/BaiTap/SS13/BaiTap3/src/test/java/com/storex/shipping/service/ShippingServiceTest.java):
```text
BUILD SUCCESSFUL in 27s
4 actionable tasks: 4 executed
```
- Checklist 1: Tự động chuyển `OPEN` $\rightarrow$ `HALF_OPEN` sau 20s `waitDurationInOpenState`.
- Checklist 2: Tại `HALF_OPEN`, cấp phép đúng 3 request trinh sát, từ chối 7 request còn lại với `CallNotPermittedException`.
