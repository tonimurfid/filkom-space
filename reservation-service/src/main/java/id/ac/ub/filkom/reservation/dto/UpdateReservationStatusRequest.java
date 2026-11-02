package id.ac.ub.filkom.reservation.dto;

import id.ac.ub.filkom.reservation.entity.ReservationStatus;
import jakarta.validation.constraints.*;

public record UpdateReservationStatusRequest(
    @NotNull ReservationStatus status,
    @Size(max = 255) String reason) {}
