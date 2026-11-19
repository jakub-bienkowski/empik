package pl.bienkowski.empik.utils.uuid;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7FactoryTest {

    @Test
    void generateId_whenCalled_thenReturnsUuidV7WithRfcVariantAndCurrentEpochMillis() {
        var factory = new UuidV7Factory();
        long before = System.currentTimeMillis();
        var id = factory.generateId();
        long after = System.currentTimeMillis();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(id.getMostSignificantBits() >>> 16).isBetween(before, after);
    }
}
