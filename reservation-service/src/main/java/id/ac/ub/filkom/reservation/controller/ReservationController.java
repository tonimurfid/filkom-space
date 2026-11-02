package id.ac.ub.filkom.reservation.controller;

import id.ac.ub.filkom.reservation.dto.*;
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
@RequestMapping("/api/v1")
public class ReservationController {
  private final ReservationService reservations;

  public ReservationController(ReservationService reservations) {
    this.reservations = reservations;
  }

  @GetMapping("/reservations")
  public ItemList<ReservationResponse> list(
      @RequestParam(required = false) String studentId,
      @RequestParam(required = false) ReservationStatus status,
      @RequestParam(required = false) UUID roomId,
      Authentication a) {
    return reservations.list(studentId, status, roomId, a);
  }

  @GetMapping("/reservations/{reservationId}")
  public ReservationResponse get(@PathVariable UUID reservationId, Authentication a) {
    return reservations.get(reservationId, a);
  }

  @GetMapping("/availability")
  public Availability availability(
      @RequestParam UUID roomId,
      @RequestParam LocalDate date,
      @RequestParam String startTime,
      @RequestParam String endTime) {
    return reservations.availability(roomId, date, startTime, endTime);
  }

  @PostMapping("/reservations")
  public ResponseEntity<ReservationResponse> create(
      @Valid @RequestBody CreateReservationRequest req, Authentication a) {
    var r = reservations.create(req, a);
    return ResponseEntity.created(URI.create("/api/v1/reservations/" + r.id())).body(r);
  }
}
