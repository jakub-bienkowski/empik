package pl.bienkowski.empik.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import static java.util.Objects.requireNonNull;

public record CreateCouponRequest(
    @Pattern(regexp = "[A-Z]{2}") String countryCode,
    @Positive int maxUsages
) {

    public CreateCouponRequest(String countryCode, int maxUsages) {
        this.countryCode = requireNonNull(countryCode, "countryCode");
        this.maxUsages = maxUsages;
    }
}
