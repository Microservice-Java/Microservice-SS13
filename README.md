# Microservice SS13 - Advanced Resilience Patterns & Circuit Breakers

Repository lưu trữ bài tập thực hành về **Resilience4j Circuit Breaker & Fallback Patterns** cho Microservice Architecture.

## Danh sách bài tập

### [Bài Tập Thực Hành 1: Triển Khai Fallback Pattern Bảo Vệ Giao Diện Trang Chủ](./BaiTap1)
- **Mục tiêu**: Xây dựng StoreX-BFF gọi sang Marketing-Service lấy danh sách Flash Voucher trang chủ.
- **Giải pháp**: Sử dụng `@CircuitBreaker(name = "voucherCircuitBreaker", fallbackMethod = "getFlashVouchersFallback")` tự động ngắt và chuyển sang Fallback khi Marketing-Service bảo trì/offline, trả về mã `DEFAULT_FREESHIP` 15K với HTTP Status 200 OK.
- **Báo cáo chi tiết**: [BaoCao_BaiTap1.md](./BaiTap1/BaoCao_BaiTap1.md)

---

## Hướng dẫn chạy và kiểm thử

### Bài Tập 1
```bash
cd BaiTap1
./gradlew test
```
