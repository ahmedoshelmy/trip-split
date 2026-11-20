package dev.tripsplit;

import org.springframework.boot.SpringApplication;

/** Runs the app locally against a Testcontainers Postgres: {@code ./mvnw spring-boot:test-run}. */
public class TestTripSplitApplication {

  public static void main(String[] args) {
    SpringApplication.from(TripSplitApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
