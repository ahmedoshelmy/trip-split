package dev.tripsplit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Trip Split: an event-sourced modular monolith (see {@code docs/adr}). */
@SpringBootApplication
public class TripSplitApplication {

  public static void main(String[] args) {
    SpringApplication.run(TripSplitApplication.class, args);
  }
}
