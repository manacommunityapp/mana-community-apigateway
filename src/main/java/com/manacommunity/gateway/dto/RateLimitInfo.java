package com.manacommunity.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RateLimitInfo {

    private long remaining;
    private long limit;
    private long resetTimestamp;
}
