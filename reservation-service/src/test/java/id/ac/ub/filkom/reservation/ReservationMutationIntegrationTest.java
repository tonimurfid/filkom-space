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

  String replacement(Reservation reservation) {
    return "{\"roomId\":\"" + ROOM + "\",\"schedule\":{\"date\":\"" + reservation.date
        + "\",\"startTime\":\"16:00\",\"endTime\":\"17:00\"},"
        + "\"purpose\":\"Revisi diskusi\",\"participantCount\":5}";
  }

  org.springframework.test.web.servlet.ResultActions v2Decision(
      UUID id, String state, boolean admin) throws Exception {
    return mvc.perform(put("/api/v2/reservations/" + id + "/decision")
        .with(admin ? user("admin").roles("ADMIN") : user(OWNER).roles("STUDENT"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + state + "\"}"));
  }

  @Test
  void v2ReplacePendingPreservesIdentityAndRejectsInvalidOrForeignUpdates() throws Exception {
    var r = pending("13:00", "15:00");
    var createdAt = repo.findById(r.id).orElseThrow().createdAt;
    String path = "/api/v2/reservations/" + r.id;
    mvc.perform(put(path).with(user(OWNER).roles("STUDENT"))
        .contentType(MediaType.APPLICATION_JSON).content(replacement(r)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.schedule.startTime").value("16:00"))
        .andExpect(jsonPath("$.studentId").value(OWNER))
        .andExpect(jsonPath("$.id").value(r.id.toString()))
        .andExpect(jsonPath("$.status").value("PENDING"));
    mvc.perform(put(path).with(user("other").roles("STUDENT"))
        .contentType(MediaType.APPLICATION_JSON).content(replacement(r)))
        .andExpect(status().isForbidden());
    mvc.perform(put(path).with(user(OWNER).roles("STUDENT"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(replacement(r).replace("\"participantCount\":5", "\"participantCount\":20")))
        .andExpect(status().isBadRequest());
    Assertions.assertEquals(5, repo.findById(r.id).orElseThrow().participantCount);
    Assertions.assertEquals(createdAt, repo.findById(r.id).orElseThrow().createdAt);
  }

  @Test
  void v2DecisionIsIdempotentAndConflictingDecisionCannotOverwriteFinalState() throws Exception {
    var r = pending("13:00", "15:00");
    v2Decision(r.id, "APPROVED", false).andExpect(status().isForbidden());
    v2Decision(r.id, "PENDING", true).andExpect(status().isBadRequest());
    v2Decision(r.id, "APPROVED", true).andExpect(status().isOk());
    v2Decision(r.id, "APPROVED", true).andExpect(status().isOk());
    v2Decision(r.id, "REJECTED", true).andExpect(status().isConflict());
    mvc.perform(put("/api/v2/reservations/" + r.id).with(user(OWNER).roles("STUDENT"))
        .contentType(MediaType.APPLICATION_JSON).content(replacement(r)))
        .andExpect(status().isConflict());
  }

  @Test
  void v2CancellationOnlyOwnerCanCancelAndDeleteIsIdempotent() throws Exception {
    var r = pending("13:00", "15:00");
    v2Decision(r.id, "APPROVED", true).andExpect(status().isOk());
    String path = "/api/v2/reservations/" + r.id;
    mvc.perform(delete(path).with(user("other").roles("STUDENT")))
        .andExpect(status().isForbidden());
    mvc.perform(delete(path).with(user("admin").roles("ADMIN")))
        .andExpect(status().isForbidden());
    mvc.perform(delete(path).with(user(OWNER).roles("STUDENT")))
        .andExpect(status().isNoContent()).andExpect(content().string(""));
    mvc.perform(delete(path).with(user(OWNER).roles("STUDENT")))
        .andExpect(status().isNoContent());
    mvc.perform(get(path).with(user(OWNER).roles("STUDENT")))
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    Assertions.assertEquals(1, repo.count());
  }

  @Test
  void v2PatchApprovesAndCancelsWhileConflictAndDependencyFailuresKeepState() throws Exception {
    var first = pending("13:00", "15:00");
    var second = pending("14:00", "16:00");
    mvc.perform(patch("/api/v2/reservations/" + first.id).with(user("admin").roles("ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.schedule.startTime").value("13:00"));
    v2Decision(second.id, "APPROVED", true).andExpect(status().isConflict());
    mvc.perform(patch("/api/v2/reservations/" + first.id).with(user(OWNER).roles("STUDENT"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"))
        .andExpect(status().isOk());
    when(client.get(ROOM)).thenThrow(new ApiException(503, "ROOM_SERVICE_UNAVAILABLE", "down"));
    v2Decision(second.id, "APPROVED", true).andExpect(status().isServiceUnavailable());
    Assertions.assertEquals(ReservationStatus.PENDING, repo.findById(second.id).orElseThrow().status);
  }

  @Test
  void v2CannotReplaceStartedOrCancelRejectedReservation() throws Exception {
    var started = pending("13:00", "15:00");
    started.date = LocalDate.now(ZoneId.of("Asia/Jakarta")).minusDays(1);
    repo.saveAndFlush(started);
    mvc.perform(put("/api/v2/reservations/" + started.id).with(user(OWNER).roles("STUDENT"))
        .contentType(MediaType.APPLICATION_JSON).content(replacement(started)))
        .andExpect(status().isConflict());
    mvc.perform(delete("/api/v2/reservations/" + started.id).with(user(OWNER).roles("STUDENT")))
        .andExpect(status().isConflict());
    var rejected = pending("16:00", "17:00");
    v2Decision(rejected.id, "REJECTED", true).andExpect(status().isOk());
    mvc.perform(delete("/api/v2/reservations/" + rejected.id).with(user(OWNER).roles("STUDENT")))
        .andExpect(status().isConflict());
  }
}
