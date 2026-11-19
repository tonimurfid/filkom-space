package id.ac.ub.filkom.reservation.service;

import id.ac.ub.filkom.reservation.client.RoomClient;
import id.ac.ub.filkom.reservation.dto.*;
import id.ac.ub.filkom.reservation.entity.*;
import id.ac.ub.filkom.reservation.exception.ApiException;
import id.ac.ub.filkom.reservation.repository.ReservationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {
  private final ReservationRepository reservations;
  private final RoomClient rooms;
  private final Clock clock;
  private final ApprovalLock approvalLock;
  private final EntityManager entityManager;

  public ReservationService(
      ReservationRepository reservations, RoomClient rooms, Clock clock,
      ApprovalLock approvalLock, EntityManager entityManager) {
    this.reservations = reservations;
    this.rooms = rooms;
    this.clock = clock;
    this.approvalLock = approvalLock;
    this.entityManager = entityManager;
  }

  private boolean admin(Authentication a) {
    return a.getAuthorities().stream().anyMatch(r -> r.getAuthority().equals("ROLE_ADMIN"));
  }

  private void forbidden() {
    throw new ApiException(403, "FORBIDDEN", "Akses reservasi ditolak.");
  }

  @Transactional(readOnly = true)
  public ItemList<ReservationResponse> list(
      String studentId, ReservationStatus status, UUID roomId, Authentication a) {
    if (!admin(a)) {
      if ((studentId != null && !studentId.equals(a.getName())) || roomId != null) forbidden();
      studentId = a.getName();
    }
    final String owner = studentId;
    Specification<Reservation> filter =
        (r, q, b) -> {
          var p = new ArrayList<Predicate>();
          if (owner != null) p.add(b.equal(r.get("studentId"), owner));
          if (status != null) p.add(b.equal(r.get("status"), status));
          if (roomId != null) p.add(b.equal(r.get("roomId"), roomId));
          return b.and(p.toArray(Predicate[]::new));
        };
    return ItemList.of(
        reservations.findAll(filter, Sort.by("createdAt").descending()).stream()
            .map(ReservationResponse::from)
            .toList());
  }

  @Transactional(readOnly = true)
  public ReservationResponse get(UUID id, Authentication a) {
    var r =
        reservations
            .findById(id)
            .orElseThrow(
                () -> new ApiException(404, "RESERVATION_NOT_FOUND", "Reservasi tidak ditemukan."));
    if (!admin(a) && !r.studentId.equals(a.getName())) forbidden();
    return ReservationResponse.from(r);
  }

  private LocalTime time(String value) {
    if (value == null || !value.matches("^([01][0-9]|2[0-3]):[0-5][0-9]$"))
      throw new ApiException(400, "INVALID_TIME", "Waktu harus HH:mm.");
    return LocalTime.parse(value);
  }

  private void schedule(LocalDate date, LocalTime start, LocalTime end) {
    if (!start.isBefore(end))
      throw new ApiException(400, "INVALID_TIME_RANGE", "Waktu mulai harus sebelum waktu selesai.");
    if (!date.atTime(start).isAfter(LocalDateTime.now(clock)))
      throw new ApiException(400, "PAST_SCHEDULE", "Waktu reservasi harus di masa mendatang.");
  }

  private RoomClient.RoomInfo room(UUID id) {
    var r = rooms.get(id);
    if (!r.isActive()) throw new ApiException(400, "ROOM_INACTIVE", "Ruangan tidak aktif.");
    return r;
  }

  @Transactional(readOnly = true)
  public Availability availability(UUID roomId, LocalDate date, String startTime, String endTime) {
    var start = time(startTime);
    var end = time(endTime);
    schedule(date, start, end);
    room(roomId);
    return new Availability(
        roomId, date, startTime, endTime, !reservations.overlaps(roomId, date, start, end));
  }

  @Transactional
  public ReservationResponse updateStatus(
      UUID id, UpdateReservationStatusRequest req, Authentication a) {
    if (req.status() == null || req.status() == ReservationStatus.PENDING)
      throw new ApiException(400, "INVALID_STATUS", "Status tujuan tidak diizinkan.");

    var r = reservations.findForUpdate(id).orElseThrow(() ->
        new ApiException(404, "RESERVATION_NOT_FOUND", "Reservasi tidak ditemukan."));
    if (!admin(a) && !r.studentId.equals(a.getName())) forbidden();

    // Every status decision (including cancellation) is serialized for a room/date.
    approvalLock.acquire(r.roomId, r.date);
    entityManager.refresh(r); // Reload a decision that may have changed while waiting for the lock.
    if (r.status == ReservationStatus.REJECTED || r.status == ReservationStatus.CANCELLED)
      throw new ApiException(409, "INVALID_STATUS_TRANSITION",
          "Reservasi sudah berstatus akhir.");

    if (admin(a)) {
      if (r.status != ReservationStatus.PENDING ||
          (req.status() != ReservationStatus.APPROVED
              && req.status() != ReservationStatus.REJECTED))
        throw new ApiException(409, "INVALID_STATUS_TRANSITION",
            "Admin hanya dapat menyetujui atau menolak reservasi PENDING.");

      if (req.status() == ReservationStatus.APPROVED) {
        schedule(r.date, r.startTime, r.endTime);
        var room = room(r.roomId);
        if (r.participantCount > room.capacity())
          throw new ApiException(400, "CAPACITY_EXCEEDED", "Jumlah peserta melebihi kapasitas.");
        if (reservations.overlaps(r.roomId, r.date, r.startTime, r.endTime))
          throw new ApiException(409, "SCHEDULE_CONFLICT",
              "Ruangan sudah dipesan pada jadwal tersebut.");
      }
    } else {
      if (req.status() != ReservationStatus.CANCELLED)
        forbidden();
      if (!r.date.atTime(r.startTime).isAfter(LocalDateTime.now(clock)))
        throw new ApiException(409, "RESERVATION_STARTED",
            "Reservasi yang sudah dimulai tidak dapat dibatalkan.");
    }

    r.status = req.status();
    r.decisionReason = req.reason();
    return ReservationResponse.from(reservations.saveAndFlush(r));
  }

  private Reservation lockedReservation(UUID id) {
    return reservations.findForUpdate(id).orElseThrow(() ->
        new ApiException(404, "RESERVATION_NOT_FOUND", "Reservasi tidak ditemukan."));
  }

  @Transactional
  public ReservationResponse replaceV2(
      UUID id, ReplaceReservationV2Request request, Authentication authentication) {
    var reservation = lockedReservation(id);
    if (admin(authentication) || !reservation.studentId.equals(authentication.getName())) forbidden();
    if (reservation.status != ReservationStatus.PENDING)
      throw new ApiException(409, "INVALID_STATUS_TRANSITION", "Hanya PENDING dapat diganti.");
    if (!reservation.date.atTime(reservation.startTime).isAfter(LocalDateTime.now(clock)))
      throw new ApiException(409, "RESERVATION_STARTED", "Reservasi sudah dimulai.");
    var start = time(request.schedule().startTime());
    var end = time(request.schedule().endTime());
    schedule(request.schedule().date(), start, end);
    approvalLock.acquire(request.roomId(), request.schedule().date());
    var targetRoom = room(request.roomId());
    if (request.participantCount() > targetRoom.capacity())
      throw new ApiException(400, "CAPACITY_EXCEEDED", "Jumlah peserta melebihi kapasitas.");
    if (reservations.overlaps(request.roomId(), request.schedule().date(), start, end))
      throw new ApiException(409, "SCHEDULE_CONFLICT", "Ruangan sudah dipesan.");
    reservation.roomId = request.roomId();
    reservation.date = request.schedule().date();
    reservation.startTime = start;
    reservation.endTime = end;
    reservation.purpose = request.purpose();
    reservation.participantCount = request.participantCount();
    return ReservationResponse.from(reservations.saveAndFlush(reservation));
  }

  @Transactional
  public ReservationResponse decideV2(
      UUID id, UpdateReservationV2StatusRequest request, Authentication authentication) {
    if (!admin(authentication)) forbidden();
    if (request.status() != ReservationStatus.APPROVED
        && request.status() != ReservationStatus.REJECTED)
      throw new ApiException(400, "INVALID_STATUS", "Keputusan harus APPROVED atau REJECTED.");
    var reservation = lockedReservation(id);
    if (reservation.status == request.status()) return ReservationResponse.from(reservation);
    return updateStatus(id,
        new UpdateReservationStatusRequest(request.status(), request.reason()), authentication);
  }

  @Transactional
  public ReservationResponse cancelV2(UUID id, String reason, Authentication authentication) {
    var reservation = lockedReservation(id);
    if (admin(authentication) || !reservation.studentId.equals(authentication.getName())) forbidden();
    if (reservation.status == ReservationStatus.CANCELLED) return ReservationResponse.from(reservation);
    return updateStatus(id,
        new UpdateReservationStatusRequest(ReservationStatus.CANCELLED, reason), authentication);
  }

  @Transactional
  public ReservationResponse patchStatusV2(
      UUID id, UpdateReservationV2StatusRequest request, Authentication authentication) {
    if (request.status() == ReservationStatus.CANCELLED) return cancelV2(id, request.reason(), authentication);
    return decideV2(id, request, authentication);
  }

  @Transactional
  public ReservationResponse create(CreateReservationRequest req, Authentication a) {
    if (!a.getName().equals(req.studentId())) forbidden();
    var start = time(req.startTime());
    var end = time(req.endTime());
    schedule(req.date(), start, end);
    var room = room(req.roomId());
    if (req.participantCount() > room.capacity())
      throw new ApiException(400, "CAPACITY_EXCEEDED", "Jumlah peserta melebihi kapasitas.");
    if (reservations.overlaps(req.roomId(), req.date(), start, end))
      throw new ApiException(
          409, "SCHEDULE_CONFLICT", "Ruangan sudah dipesan pada jadwal tersebut.");
    var r = new Reservation();
    r.roomId = req.roomId();
    r.studentId = req.studentId();
    r.date = req.date();
    r.startTime = start;
    r.endTime = end;
    r.purpose = req.purpose();
    r.participantCount = req.participantCount();
    r.createdAt = OffsetDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    return ReservationResponse.from(reservations.saveAndFlush(r));
  }
}
