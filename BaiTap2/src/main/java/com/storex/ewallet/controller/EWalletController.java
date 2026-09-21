package com.storex.ewallet.controller;

import com.storex.ewallet.dto.EWalletDeductRequest;
import com.storex.ewallet.dto.EWalletDeductResponse;
import com.storex.ewallet.service.EWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ewallet")
@RequiredArgsConstructor
public class EWalletController {

    private final EWalletService eWalletService;

    @PostMapping("/deduct")
    public ResponseEntity<EWalletDeductResponse> deductBalance(@RequestBody EWalletDeductRequest request) {
        EWalletDeductResponse response = eWalletService.deductBalance(request);
        return ResponseEntity.ok(response);
    }
}
