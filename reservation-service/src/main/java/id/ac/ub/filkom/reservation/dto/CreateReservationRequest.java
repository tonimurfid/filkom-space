package id.ac.ub.filkom.reservation.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record CreateReservationRequest(
    @NotNull UUID roomId,
    @NotNull @Size(min = 5) String studentId,
    @NotNull LocalDate date,
    @NotNull @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$") String startTime,
    @NotNull @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$") String endTime,
    @NotNull @Size(min = 5, max = 255) String purpose,
    @NotNull @Min(1) Integer participantCount) {}
