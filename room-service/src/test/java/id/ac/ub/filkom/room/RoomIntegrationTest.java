package id.ac.ub.filkom.room;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import id.ac.ub.filkom.room.repository.RoomRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired RoomRepository rooms;
  @Autowired ObjectMapper json;
  final String payload =
      "{\"code\":\"SIM-001\",\"name\":\"Room Simulasi\",\"building\":\"G"
          + " (simulasi)\",\"floor\":2,\"capacity\":8,\"facilities\":[\"WIFI\",\"PROYEKTOR\"]}";

  @BeforeEach
  void clean() {
    rooms.deleteAll();
  }

  String createRoom() throws Exception {
    return mvc.perform(
            post("/api/v1/rooms")
                .with(httpBasic("admin", "test-admin-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.isActive").value(true))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void v2RoomRoutesEnforceAdminAndShareDataWithV1() throws Exception {
    var path = "/api/v2/rooms";
    mvc.perform(post(path).with(user("student").roles("STUDENT"))
            .contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isForbidden());
    mvc.perform(post(path).with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"isActive\":false," + payload.substring(1)))
        .andExpect(status().isBadRequest());
    var response = mvc.perform(post(path).with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isActive").value(true)).andReturn().getResponse();
    String id = json.readTree(response.getContentAsString()).get("id").asText();
    Assertions.assertEquals(path + "/" + id, response.getHeader("Location"));
    mvc.perform(get(path + "/" + id)).andExpect(status().isOk());
    mvc.perform(get(path).param("minCapacity", "8")).andExpect(jsonPath("$.total").value(1));
    mvc.perform(get("/api/v1/rooms/" + id)).andExpect(status().isOk());
  }

  @Test
  void createPersistsAndPublicGetReturnsContract() throws Exception {
    var response = json.readTree(createRoom());
    UUID.fromString(response.get("id").asText());
    Assertions.assertEquals(1, rooms.count());
    mvc.perform(get("/api/v1/rooms/" + response.get("id").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SIM-001"))
        .andExpect(jsonPath("$.facilities[0]").value("WIFI"));
    mvc.perform(get("/api/v1/rooms"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].capacity").value(8));
  }

  @Test
  void filtersCombineAndExclude() throws Exception {
    createRoom();
    mvc.perform(
            get("/api/v1/rooms")
                .param("minCapacity", "8")
                .param("building", "G (simulasi)")
                .param("facility", "WIFI"))
        .andExpect(jsonPath("$.total").value(1));
    for (var p :
        List.of(
            new String[] {"minCapacity", "9"},
            new String[] {"building", "F"},
            new String[] {"facility", "AC"}))
      mvc.perform(get("/api/v1/rooms").param(p[0], p[1])).andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void duplicateReturns409() throws Exception {
    createRoom();
    mvc.perform(
            post("/api/v1/rooms")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CODE_CONFLICT"));
  }

  @Test
  void validatesRequiredFieldsAndLimits() throws Exception {
    for (String body :
        List.of(
            "{}",
            payload.replace("\"capacity\":8", "\"capacity\":0"),
            payload.replace("\"floor\":2", "\"floor\":0"),
            payload.replace("SIM-001", "AB"),
            payload.replace("[\"WIFI\",\"PROYEKTOR\"]", "null")))
      mvc.perform(
              post("/api/v1/rooms")
                  .with(user("admin").roles("ADMIN"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.code").exists());
    mvc.perform(get("/api/v1/rooms").param("minCapacity", "0")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/rooms/not-uuid")).andExpect(status().isBadRequest());
  }

  @Test
  void authenticationAndRoleAreEnforced() throws Exception {
    mvc.perform(post("/api/v1/rooms").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
    mvc.perform(
            post("/api/v1/rooms")
                .with(httpBasic("admin", "wrong"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/rooms")
                .with(httpBasic("235150200111001", "test-student-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  void missingRoomReturns404() throws Exception {
    mvc.perform(get("/api/v1/rooms/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
  }

  @Test
  void swaggerAndRuntimeDocsAccessible() throws Exception {
    mvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/rooms'].post").exists());
  }
}
