package com.storex.bff.controller;

import com.storex.bff.dto.FlashVoucherDto;
import com.storex.bff.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @GetMapping("/flash")
    public ResponseEntity<List<FlashVoucherDto>> getFlashVouchers() {
        List<FlashVoucherDto> vouchers = voucherService.getFlashVouchers();
        return ResponseEntity.ok(vouchers);
    }
}
