package id.ac.ub.filkom.reservation.controller;

import id.ac.ub.filkom.reservation.dto.AvailabilityV2;
import id.ac.ub.filkom.reservation.dto.CreateReservationV2Request;
import id.ac.ub.filkom.reservation.dto.ItemList;
import id.ac.ub.filkom.reservation.dto.ReservationV2Response;
import id.ac.ub.filkom.reservation.entity.ReservationStatus;
import id.ac.ub.filkom.reservation.service.ReservationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2")
public class ReservationV2Controller {
  private final ReservationService reservations;

  public ReservationV2Controller(ReservationService reservations) {
    this.reservations = reservations;
  }

  @GetMapping("/reservations")
  public ItemList<ReservationV2Response> list(
      @RequestParam(required = false) String studentId,
      @RequestParam(required = false) ReservationStatus status,
      @RequestParam(required = false) UUID roomId,
      Authentication authentication) {
    var result = reservations.list(studentId, status, roomId, authentication);
    return new ItemList<>(result.items().stream().map(ReservationV2Response::from).toList(),
        result.total());
  }

  @GetMapping("/reservations/{reservationId}")
  public ReservationV2Response get(@PathVariable UUID reservationId, Authentication authentication) {
    return ReservationV2Response.from(reservations.get(reservationId, authentication));
  }

  @GetMapping("/availability")
  public AvailabilityV2 availability(
      @RequestParam UUID roomId,
      @RequestParam LocalDate date,
      @RequestParam String startTime,
      @RequestParam String endTime) {
    return AvailabilityV2.from(reservations.availability(roomId, date, startTime, endTime));
  }

  @PostMapping("/reservations")
  public ResponseEntity<ReservationV2Response> create(
      @Valid @RequestBody CreateReservationV2Request request, Authentication authentication) {
    var reservation = ReservationV2Response.from(
        reservations.create(request.toCreateReservationRequest(), authentication));
    return ResponseEntity.created(URI.create("/api/v2/reservations/" + reservation.id()))
        .body(reservation);
  }
}
