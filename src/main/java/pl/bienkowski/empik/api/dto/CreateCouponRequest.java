package pl.bienkowski.empik.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import static java.util.Objects.requireNonNull;

public record CreateCouponRequest(
    @NotBlank @Size(min = 5) String code,
    @Pattern(regexp = "[A-Z]{2}") String countryCode,
    @Positive int maxUsages
) {

    public CreateCouponRequest(String code, String countryCode, int maxUsages) {
        this.code = requireNonNull(code, "code");
        this.countryCode = requireNonNull(countryCode, "countryCode");
        this.maxUsages = maxUsages;
    }
}
