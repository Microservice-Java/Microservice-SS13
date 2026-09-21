package com.storex.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingFeeRequest {
    private String orderId;
    private Double weightKg;
    private String destinationCity;
}
