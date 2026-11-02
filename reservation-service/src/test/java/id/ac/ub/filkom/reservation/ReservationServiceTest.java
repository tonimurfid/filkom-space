package id.ac.ub.filkom.reservation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import id.ac.ub.filkom.reservation.client.RoomClient;
import id.ac.ub.filkom.reservation.dto.CreateReservationRequest;
import id.ac.ub.filkom.reservation.exception.ApiException;
import id.ac.ub.filkom.reservation.repository.ReservationRepository;
import id.ac.ub.filkom.reservation.service.ReservationService;
import id.ac.ub.filkom.reservation.service.ApprovalLock;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
  @Mock ReservationRepository repo;
  @Mock RoomClient client;
  final Clock clock = Clock.fixed(Instant.parse("2026-11-01T03:00:00Z"), ZoneId.of("Asia/Jakarta"));

  @Test
  void sameDayPastOrEqualStartUsesJakartaClock() {
    var service = new ReservationService(repo, client, clock, mock(ApprovalLock.class), mock(EntityManager.class));
    for (var start : new String[] {"09:59", "10:00"}) {
      var req =
          new CreateReservationRequest(
              UUID.randomUUID(),
              "student",
              LocalDate.of(2026, 11, 1),
              start,
              "11:00",
              "Diskusi",
              1);
      var ex =
          assertThrows(
              ApiException.class,
              () ->
                  service.create(
                      req, new TestingAuthenticationToken("student", "", "ROLE_STUDENT")));
      assertEquals("PAST_SCHEDULE", ex.code);
    }
    verifyNoInteractions(repo, client);
  }

  @Test
  void roomFailureNeverSaves() {
    UUID id = UUID.randomUUID();
    when(client.get(id))
        .thenThrow(new ApiException(503, "ROOM_SERVICE_UNAVAILABLE", "unavailable"));
    var req =
        new CreateReservationRequest(
            id, "student", LocalDate.of(2026, 11, 2), "10:00", "11:00", "Diskusi", 1);
    assertThrows(
        ApiException.class,
        () ->
            new ReservationService(repo, client, clock, mock(ApprovalLock.class), mock(EntityManager.class))
                .create(req, new TestingAuthenticationToken("student", "", "ROLE_STUDENT")));
    verifyNoInteractions(repo);
  }
}
