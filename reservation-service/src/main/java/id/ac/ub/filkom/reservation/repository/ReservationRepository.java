package id.ac.ub.filkom.reservation.repository;

import id.ac.ub.filkom.reservation.entity.*;
import java.time.*;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository
    extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Reservation r where r.id = :id")
  Optional<Reservation> findForUpdate(@Param("id") UUID id);

  @Query(
      "select count(r)>0 from Reservation r where r.roomId=:roomId and r.date=:date and"
          + " r.status=id.ac.ub.filkom.reservation.entity.ReservationStatus.APPROVED and"
          + " r.startTime<:endTime and r.endTime>:startTime")
  boolean overlaps(
      @Param("roomId") UUID roomId,
      @Param("date") LocalDate date,
      @Param("startTime") LocalTime startTime,
      @Param("endTime") LocalTime endTime);
}
