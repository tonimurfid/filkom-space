package id.ac.ub.filkom.room.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;

public record UpdateRoomV2StatusRequest(
    @NotNull Boolean isActive) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown property: " + name);
  }
}
