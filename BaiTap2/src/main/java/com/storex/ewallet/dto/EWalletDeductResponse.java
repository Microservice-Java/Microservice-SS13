package com.storex.ewallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EWalletDeductResponse {
    private String status;
    private String transactionId;
    private String message;
}
