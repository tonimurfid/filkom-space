package id.ac.ub.filkom.reservation.dto;

import id.ac.ub.filkom.reservation.entity.*;
import java.time.*;
import java.util.UUID;

public record ReservationResponse(
    UUID id,
    UUID roomId,
    String studentId,
    LocalDate date,
    String startTime,
    String endTime,
    String purpose,
    int participantCount,
    ReservationStatus status,
    OffsetDateTime createdAt) {
  public static ReservationResponse from(Reservation r) {
    return new ReservationResponse(
        r.id,
        r.roomId,
        r.studentId,
        r.date,
        r.startTime.toString(),
        r.endTime.toString(),
        r.purpose,
        r.participantCount,
        r.status,
        r.createdAt.atZoneSameInstant(ZoneId.of("Asia/Jakarta")).toOffsetDateTime());
  }
}
