package com.storex.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingFeeResponse {
    private String status;
    private Double shippingFee;
    private String provider;
    private String message;
}
