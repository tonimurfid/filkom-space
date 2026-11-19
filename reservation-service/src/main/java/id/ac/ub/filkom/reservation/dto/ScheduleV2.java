package id.ac.ub.filkom.reservation.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record ScheduleV2(
    @NotNull LocalDate date,
    @NotNull @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$") String startTime,
    @NotNull @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$") String endTime) {
  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown schedule property: " + name);
  }
}
