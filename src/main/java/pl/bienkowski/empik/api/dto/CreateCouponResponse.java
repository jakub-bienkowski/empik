package pl.bienkowski.empik.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateCouponResponse(
        UUID code,
        String countryCode,
        int maxUsages,
        int currentUsages,
        Instant createdAt
) {
}
