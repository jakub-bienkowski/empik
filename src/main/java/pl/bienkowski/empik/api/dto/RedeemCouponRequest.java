package pl.bienkowski.empik.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

public record RedeemCouponRequest(
        UUID code,
        @NotBlank @Size(max = 255) String userId
) {

    @JsonCreator
    public RedeemCouponRequest(UUID code, String userId) {
        this.code = requireNonNull(code, "code");
        this.userId = requireNonNull(userId, "userId");
    }
}
