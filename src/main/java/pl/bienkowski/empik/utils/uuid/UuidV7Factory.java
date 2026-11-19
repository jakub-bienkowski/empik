package pl.bienkowski.empik.utils.uuid;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

@Component
public final class UuidV7Factory implements IdFactory<UUID> {

    private final SecureRandom random = new SecureRandom();

    @Override
    public UUID generateId() {
        long timestamp = System.currentTimeMillis() & 0xFFFF_FFFF_FFFFL;
        long mostSignificantBits = (timestamp << 16) | 0x7000L | (random.nextInt() & 0x0FFFL);
        long leastSignificantBits = 0x8000_0000_0000_0000L | (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL);
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
