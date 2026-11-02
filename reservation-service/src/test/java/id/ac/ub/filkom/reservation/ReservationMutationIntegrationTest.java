package id.ac.ub.filkom.reservation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import id.ac.ub.filkom.reservation.client.RoomClient;
import id.ac.ub.filkom.reservation.entity.*;
import id.ac.ub.filkom.reservation.exception.ApiException;
import id.ac.ub.filkom.reservation.repository.ReservationRepository;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationMutationIntegrationTest {
  static final UUID ROOM = UUID.randomUUID();
  static final String OWNER = "235150200111001";
  @Autowired MockMvc mvc;
  @Autowired ReservationRepository repo;
  @MockitoBean RoomClient client;

  @BeforeEach
  void clean() {
    repo.deleteAll();
    reset(client);
    when(client.get(ROOM)).thenReturn(new RoomClient.RoomInfo(ROOM, 8, true));
  }

  Reservation pending(String start, String end) {
    var r = new Reservation();
    r.roomId = ROOM;
    r.studentId = OWNER;
    r.date = LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(3);
    r.startTime = LocalTime.parse(start);
    r.endTime = LocalTime.parse(end);
    r.purpose = "Diskusi penelitian";
    r.participantCount = 6;
    r.createdAt = OffsetDateTime.now();
    return repo.saveAndFlush(r);
  }

  org.springframework.test.web.servlet.ResultActions decision(UUID id, String payload, boolean admin)
      throws Exception {
    return mvc.perform(patch("/api/v1/reservations/" + id)
        .with(admin ? user("admin").roles("ADMIN") : user(OWNER).roles("STUDENT"))
        .contentType(MediaType.APPLICATION_JSON).content(payload));
  }

  @Test
  void approvedBlocksSlotAndStudentCanCancelBeforeStart() throws Exception {
    var r = pending("13:00", "15:00");
    decision(r.id, "{\"status\":\"APPROVED\",\"reason\":\"Disetujui\"}", true)
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    mvc.perform(get("/api/v1/availability").param("roomId", ROOM.toString())
        .param("date", r.date.toString()).param("startTime", "14:00")
        .param("endTime", "16:00")).andExpect(jsonPath("$.available").value(false));
    decision(r.id, "{\"status\":\"CANCELLED\"}", false)
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    mvc.perform(get("/api/v1/availability").param("roomId", ROOM.toString())
        .param("date", r.date.toString()).param("startTime", "14:00")
        .param("endTime", "16:00")).andExpect(jsonPath("$.available").value(true));
  }

  @Test
  void competingPendingReservationsCannotBothBeApproved() throws Exception {
    var first = pending("13:00", "15:00");
    var second = pending("14:00", "16:00");
    decision(first.id, "{\"status\":\"APPROVED\"}", true)
        .andExpect(status().isOk());
    decision(second.id, "{\"status\":\"APPROVED\"}", true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));
    Assertions.assertEquals(ReservationStatus.PENDING, repo.findById(second.id).orElseThrow().status);
  }

  @Test
  void rejectIsFinalAndPermissionsAreEnforced() throws Exception {
    var r = pending("13:00", "15:00");
    decision(r.id, "{\"status\":\"APPROVED\"}", false)
        .andExpect(status().isForbidden());
    decision(r.id, "{\"status\":\"CANCELLED\"}", true)
        .andExpect(status().isConflict());
    decision(r.id, "{\"status\":\"REJECTED\"}", true)
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    decision(r.id, "{\"status\":\"CANCELLED\"}", false)
        .andExpect(status().isConflict());
    decision(r.id, "{\"status\":\"PENDING\"}", true)
        .andExpect(status().isBadRequest());
    mvc.perform(patch("/api/v1/reservations/" + r.id)
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void approvalRechecksRoomAndCapacityAndDoesNotChangeStatusOnFailure() throws Exception {
    var r = pending("13:00", "15:00");
    when(client.get(ROOM)).thenReturn(new RoomClient.RoomInfo(ROOM, 5, true));
    decision(r.id, "{\"status\":\"APPROVED\"}", true)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CAPACITY_EXCEEDED"));
    when(client.get(ROOM)).thenThrow(new ApiException(503, "ROOM_SERVICE_UNAVAILABLE", "down"));
    decision(r.id, "{\"status\":\"APPROVED\"}", true)
        .andExpect(status().isServiceUnavailable());
    Assertions.assertEquals(ReservationStatus.PENDING, repo.findById(r.id).orElseThrow().status);
  }
}
