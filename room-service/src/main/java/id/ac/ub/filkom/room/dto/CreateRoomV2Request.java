package id.ac.ub.filkom.room.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateRoomV2Request(
    @NotNull @Size(min = 3, max = 30) String code,
    @NotNull @Size(min = 3, max = 100) String name,
    @NotNull @Size(min = 1) String building,
    @NotNull @Min(1) Integer floor,
    @NotNull @Min(1) Integer capacity,
    @NotNull List<@NotNull String> facilities) {
  public CreateRoomRequest toCreateRoomRequest() {
    return new CreateRoomRequest(code, name, building, floor, capacity, facilities);
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown room property: " + name);
  }
}
