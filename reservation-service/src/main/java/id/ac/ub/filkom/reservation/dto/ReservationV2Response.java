package id.ac.ub.filkom.reservation.dto;

import id.ac.ub.filkom.reservation.entity.ReservationStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationV2Response(
    UUID id,
    UUID roomId,
    String studentId,
    ScheduleV2 schedule,
    String purpose,
    int participantCount,
    ReservationStatus status,
    OffsetDateTime createdAt) {
  public static ReservationV2Response from(ReservationResponse reservation) {
    return new ReservationV2Response(
        reservation.id(), reservation.roomId(), reservation.studentId(),
        new ScheduleV2(reservation.date(), reservation.startTime(), reservation.endTime()),
        reservation.purpose(), reservation.participantCount(), reservation.status(),
        reservation.createdAt());
  }
}
