package pl.bienkowski.empik.api.controller;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final ApplicationAvailability availability;
    private final DatabaseClient database;

    public HealthController(ApplicationAvailability availability, DatabaseClient database) {
        this.availability = availability;
        this.database = database;
    }

    @GetMapping("/liveness")
    public ResponseEntity<Void> liveness() {
        return buildResponseEntity(availability.getLivenessState() == LivenessState.CORRECT);
    }

    @GetMapping("/readiness")
    public Mono<ResponseEntity<Void>> readiness() {
        if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) {
            return Mono.just(buildResponseEntity(false));
        }

        return database.sql("SELECT 1")
                .map((_, _) -> true)
                .one()
                .timeout(Duration.ofSeconds(2))
                .defaultIfEmpty(false)
                .onErrorReturn(DataAccessException.class, false)
                .onErrorReturn(TimeoutException.class, false)
                .map(this::buildResponseEntity);
    }

    private ResponseEntity<Void> buildResponseEntity(boolean healthy) {
        return healthy ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
