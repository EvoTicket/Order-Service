package com.capstone.orderservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBankAccountResponse {
    private String bankCode;
    private String bankAccountNumber;
    private String bankAccountName;
}
