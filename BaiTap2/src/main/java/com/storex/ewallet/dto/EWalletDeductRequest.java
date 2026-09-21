package com.storex.ewallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EWalletDeductRequest {
    private String orderId;
    private String userId;
    private Double amount;
}
