package id.ac.ub.filkom.reservation.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record ReplaceReservationV2Request(
    @NotNull UUID roomId,
    @NotNull @Valid ScheduleV2 schedule,
    @NotNull @Size(min = 5, max = 255) String purpose,
    @NotNull @Min(1) Integer participantCount) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown property: " + name);
  }
}
