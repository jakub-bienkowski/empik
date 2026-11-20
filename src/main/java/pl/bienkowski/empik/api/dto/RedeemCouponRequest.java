package pl.bienkowski.empik.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static java.util.Objects.requireNonNull;

public record RedeemCouponRequest(
        @NotBlank @Size(min = 5) String code,
        @NotBlank @Size(max = 255) String userId
) {

    @JsonCreator
    public RedeemCouponRequest(String code, String userId) {
        this.code = requireNonNull(code, "code");
        this.userId = requireNonNull(userId, "userId");
    }
}
