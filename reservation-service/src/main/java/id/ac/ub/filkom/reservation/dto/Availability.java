package id.ac.ub.filkom.reservation.dto;

import java.time.LocalDate;
import java.util.UUID;

public record Availability(
    UUID roomId, LocalDate date, String startTime, String endTime, boolean available) {}
