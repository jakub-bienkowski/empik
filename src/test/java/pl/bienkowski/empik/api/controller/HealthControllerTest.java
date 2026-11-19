package pl.bienkowski.empik.api.controller;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.function.BiFunction;

import org.mockito.ArgumentMatchers;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private final ApplicationAvailability availability = mock(ApplicationAvailability.class);
    private final DatabaseClient database = mock(DatabaseClient.class);
    private final WebTestClient client = WebTestClient.bindToController(new HealthController(availability, database))
            .build();

    @Test
    void liveness_whenAvailabilityChanges_thenReportsStateWithoutQueryingDatabase() {
        when(availability.getLivenessState()).thenReturn(LivenessState.CORRECT, LivenessState.BROKEN);
        client.get().uri("/health/liveness").exchange().expectStatus().isOk();
        client.get().uri("/health/liveness").exchange().expectStatus().isEqualTo(503);
        verifyNoInteractions(database);
    }

    @Test
    void readiness_whenAppRefusesTraffic_thenReturnsUnavailableWithoutQueryingDatabase() {
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        client.get().uri("/health/readiness").exchange().expectStatus().isEqualTo(503);
        verifyNoInteractions(database);
    }

    @Test
    void readiness_whenAppAcceptsTraffic_thenReflectsDatabaseHealth() {
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        var query = mock(DatabaseClient.GenericExecuteSpec.class);
        RowsFetchSpec<Boolean> result = mock();
        when(database.sql("SELECT 1")).thenReturn(query);
        when(query.map(ArgumentMatchers.<BiFunction<Row, RowMetadata, Boolean>>any()))
                .thenReturn(result);
        when(result.one()).thenReturn(Mono.just(true), Mono.error(new DataAccessResourceFailureException("offline")));
        client.get().uri("/health/readiness").exchange().expectStatus().isOk();
        client.get().uri("/health/readiness").exchange().expectStatus().isEqualTo(503);
    }
}
