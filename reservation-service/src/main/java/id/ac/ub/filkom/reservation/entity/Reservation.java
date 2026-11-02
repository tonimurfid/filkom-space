package id.ac.ub.filkom.reservation.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(
    name = "reservations",
    indexes = @Index(name = "reservation_schedule_idx", columnList = "roomId,date,status"))
public class Reservation {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @Column(nullable = false)
  public UUID roomId;

  @Column(nullable = false, columnDefinition = "text")
  public String studentId;

  @Column(nullable = false)
  public LocalDate date;

  @Column(nullable = false)
  public LocalTime startTime;

  @Column(nullable = false)
  public LocalTime endTime;

  @Column(nullable = false, length = 255)
  public String purpose;

  @Column(nullable = false)
  public int participantCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public ReservationStatus status = ReservationStatus.PENDING;

  @Column(nullable = false)
  public OffsetDateTime createdAt;
}
