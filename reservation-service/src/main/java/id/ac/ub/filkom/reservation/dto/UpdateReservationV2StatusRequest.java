package id.ac.ub.filkom.reservation.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import id.ac.ub.filkom.reservation.entity.ReservationStatus;

public record UpdateReservationV2StatusRequest(
    @NotNull ReservationStatus status,
    @Size(max = 255) String reason) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown property: " + name);
  }
}
