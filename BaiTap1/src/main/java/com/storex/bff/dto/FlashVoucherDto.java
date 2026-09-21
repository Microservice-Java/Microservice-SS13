package com.storex.bff.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashVoucherDto {

    @JsonProperty("voucher_code")
    private String voucherCode;

    @JsonProperty("discount_amount")
    private Double discountAmount;

    @JsonProperty("description")
    private String description;
}
