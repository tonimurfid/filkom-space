package id.ac.ub.filkom.reservation.dto;

import java.util.UUID;

public record AvailabilityV2(UUID roomId, ScheduleV2 schedule, boolean available) {
  public static AvailabilityV2 from(Availability availability) {
    return new AvailabilityV2(
        availability.roomId(),
        new ScheduleV2(availability.date(), availability.startTime(), availability.endTime()),
        availability.available());
  }
}
