package id.ac.ub.filkom.room;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ub.filkom.room.client.RoomReservationClient;
import id.ac.ub.filkom.room.exception.ApiException;
import id.ac.ub.filkom.room.repository.RoomRepository;
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
class RoomMutationIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired RoomRepository repo;
  @Autowired ObjectMapper json;
  @MockitoBean RoomReservationClient reservationClient;

  static final String BODY =
      "{\"code\":\"SIM-MOD-01\",\"name\":\"Ruang Penelitian\",\"building\":\"Gedung Simulasi\","
          + "\"floor\":2,\"capacity\":8,\"facilities\":[\"WIFI\"]}";

  @BeforeEach
  void clean() {
    repo.deleteAll();
    reset(reservationClient);
  }

  UUID create() throws Exception {
    var res = mvc.perform(post("/api/v1/rooms").with(user("admin").roles("ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isCreated()).andReturn();
    return UUID.fromString(json.readTree(res.getResponse().getContentAsString()).get("id").asText());
  }

  @Test
  void putReplacesAllEditableFieldsButPreservesIdAndStatus() throws Exception {
    var id = create();
    String replacement = "{\"code\":\"SIM-MOD-02\",\"name\":\"Ruang Baru\","
        + "\"building\":\"Gedung F\",\"floor\":3,\"capacity\":12,"
        + "\"facilities\":[\"PROYEKTOR\"]}";
    mvc.perform(put("/api/v1/rooms/" + id).with(user("admin").roles("ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content(replacement))
        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.code").value("SIM-MOD-02"))
        .andExpect(jsonPath("$.capacity").value(12))
        .andExpect(jsonPath("$.facilities[0]").value("PROYEKTOR"))
        .andExpect(jsonPath("$.isActive").value(true));
    mvc.perform(put("/api/v1/rooms/" + id).with(user("admin").roles("ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchOnlyChangesActiveState() throws Exception {
    var id = create();
    mvc.perform(patch("/api/v1/rooms/" + id + "/status").with(user("admin").roles("ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isActive").value(false))
        .andExpect(jsonPath("$.name").value("Ruang Penelitian"));
    mvc.perform(patch("/api/v1/rooms/" + id + "/status").with(user("admin").roles("ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void archiveHidesRoomAndIsIdempotent() throws Exception {
    var id = create();
    mvc.perform(delete("/api/v1/rooms/" + id).with(user("admin").roles("ADMIN")))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/rooms/" + id)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/rooms")).andExpect(jsonPath("$.total").value(0));
    mvc.perform(delete("/api/v1/rooms/" + id).with(user("admin").roles("ADMIN")))
        .andExpect(status().isNoContent());
    Assertions.assertEquals(1, repo.count());
  }

  @Test
  void archiveProtectsFutureBookingsAndDependencyOutage() throws Exception {
    var id = create();
    when(reservationClient.hasFutureBookings(id)).thenReturn(true);
    mvc.perform(delete("/api/v1/rooms/" + id).with(user("admin").roles("ADMIN")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_HAS_RESERVATIONS"));
    when(reservationClient.hasFutureBookings(id)).thenThrow(
        new ApiException(503, "RESERVATION_SERVICE_UNAVAILABLE", "offline"));
    mvc.perform(delete("/api/v1/rooms/" + id).with(user("admin").roles("ADMIN")))
        .andExpect(status().isServiceUnavailable());
    mvc.perform(get("/api/v1/rooms/" + id)).andExpect(status().isOk());
  }

  @Test
  void studentCannotModifyRoom() throws Exception {
    var id = create();
    mvc.perform(put("/api/v1/rooms/" + id).with(user("student").roles("STUDENT"))
        .contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isForbidden());
    mvc.perform(patch("/api/v1/rooms/" + id + "/status")
        .with(user("student").roles("STUDENT"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false}"))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/rooms/" + id).with(user("student").roles("STUDENT")))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/rooms/" + id)).andExpect(status().isUnauthorized());
  }

  @Test
  void v2ReplacementRequiresActiveAndPatchPreservesMetadata() throws Exception {
    var id = create();
    String path = "/api/v2/rooms/" + id;
    mvc.perform(put(path).with(user("admin").roles("ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isBadRequest());
    String replacement = BODY.substring(0, BODY.length() - 1) + ",\"isActive\":false}";
    mvc.perform(put(path).with(user("admin").roles("ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content(replacement))
        .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false));
    mvc.perform(patch(path + "/status").with(user("admin").roles("ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":true}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(true))
        .andExpect(jsonPath("$.name").value("Ruang Penelitian"));
    mvc.perform(patch(path + "/status").with(user("admin").roles("ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":true,\"capacity\":3}"))
        .andExpect(status().isBadRequest());
    mvc.perform(put(path).with(user("student").roles("STUDENT"))
        .contentType(MediaType.APPLICATION_JSON).content(replacement))
        .andExpect(status().isForbidden());
  }

  @Test
  void v2ArchiveProtectsBookingsAndRepeatsAfterSuccess() throws Exception {
    var id = create();
    String path = "/api/v2/rooms/" + id;
    when(reservationClient.hasFutureBookings(id)).thenReturn(true);
    mvc.perform(delete(path).with(user("admin").roles("ADMIN")))
        .andExpect(status().isConflict());
    when(reservationClient.hasFutureBookings(id)).thenReturn(false);
    mvc.perform(delete(path).with(user("admin").roles("ADMIN")))
        .andExpect(status().isNoContent()).andExpect(content().string(""));
    mvc.perform(delete(path).with(user("admin").roles("ADMIN")))
        .andExpect(status().isNoContent());
    mvc.perform(get(path)).andExpect(status().isNotFound());
    Assertions.assertEquals(1, repo.count());
  }
}
