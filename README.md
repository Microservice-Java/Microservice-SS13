# Microservice SS13 - Advanced Resilience Patterns & Circuit Breakers

Repository lưu trữ bài tập thực hành về **Resilience4j Circuit Breaker & Fallback Patterns** cho Microservice Architecture.

## Danh sách bài tập

### [Bài Tập Thực Hành 1: Triển Khai Fallback Pattern Bảo Vệ Giao Diện Trang Chủ](./BaiTap1)
- **Mục tiêu**: Xây dựng StoreX-BFF gọi sang Marketing-Service lấy danh sách Flash Voucher trang chủ.
- **Giải pháp**: `@CircuitBreaker(name = "voucherCircuitBreaker", fallbackMethod = "getFlashVouchersFallback")` trả về mã `DEFAULT_FREESHIP` 15K với HTTP Status 200 OK khi bị gián đoạn.
- **Báo cáo chi tiết**: [BaoCao_BaiTap1.md](./BaiTap1/BaoCao_BaiTap1.md)

---

### [Bài Tập Thực Hành 2: Cấu Hình Cửa Sổ Trượt Count-Based Và Bẫy Ngoại Lệ Nghiệp Vụ](./BaiTap2)
- **Mục tiêu**: Phân biệt lỗi nghiệp vụ người dùng hết tiền (`InsufficientBalanceException`) vs lỗi hệ thống treo mạng (`TimeoutException`).
- **Giải pháp**: Cấu hình `ignoreExceptions` phớt lờ lỗi hết tiền để Cầu dao luôn `CLOSED`, và `recordExceptions` tính lỗi timeout để mở mạch `OPEN` khi $5/5$ call bị timeout.
- **Báo cáo chi tiết**: [BaoCao_BaiTap2.md](./BaiTap2/BaoCao_BaiTap2.md)

---

### [Bài Tập Thực Hành 3: Thử Lửa Với Trạng Thái Half-Open Và Time-Based](./BaiTap3)
- **Mục tiêu**: Mô phỏng hợp đồng SLA tính phí vận chuyển `Shipping-Service` theo cửa sổ `TIME_BASED`.
- **Giải pháp**: Cấu hình `slidingWindowType: TIME_BASED`, `slidingWindowSize: 30`, `waitDurationInOpenState: 20s`, `permittedNumberOfCallsInHalfOpenState: 3`, và `automaticTransitionFromOpenToHalfOpenEnabled: true`.
- **Báo cáo chi tiết**: [BaoCao_BaiTap3.md](./BaiTap3/BaoCao_BaiTap3.md)

---

## Hướng dẫn chạy và kiểm thử

### Bài Tập 1
```bash
cd BaiTap1
./gradlew test
```

### Bài Tập 2
```bash
cd BaiTap2
./gradlew test
```

### Bài Tập 3
```bash
cd BaiTap3
./gradlew test
```
