package com.storex.shipping.controller;

import com.storex.shipping.dto.ShippingFeeRequest;
import com.storex.shipping.dto.ShippingFeeResponse;
import com.storex.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    @PostMapping("/calculate")
    public ResponseEntity<ShippingFeeResponse> calculateShippingFee(@RequestBody ShippingFeeRequest request) {
        ShippingFeeResponse response = shippingService.calculateShippingFee(request);
        return ResponseEntity.ok(response);
    }
}
