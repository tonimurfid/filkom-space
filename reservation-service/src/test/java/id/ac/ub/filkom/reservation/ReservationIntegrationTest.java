package id.ac.ub.filkom.reservation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import id.ac.ub.filkom.reservation.entity.*;
import id.ac.ub.filkom.reservation.repository.ReservationRepository;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationIntegrationTest {
  static final String OWNER = "235150200111001";
  static final UUID ROOM = UUID.randomUUID();
  static final LocalDate DATE = LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(2);
  static HttpServer server;
  static volatile int remoteStatus = 200;
  static volatile boolean active = true;
  static AtomicInteger calls = new AtomicInteger();

  static {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/api/v1/rooms/",
          x -> {
            calls.incrementAndGet();
            String body =
                remoteStatus == 200
                    ? "{\"id\":\"" + ROOM + "\",\"capacity\":8,\"isActive\":" + active + "}"
                    : "{}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().set("Content-Type", "application/json");
            x.sendResponseHeaders(remoteStatus, bytes.length);
            x.getResponseBody().write(bytes);
            x.close();
          });
      server.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("app.room-service-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    server.stop(0);
  }

  @Autowired MockMvc mvc;
  @Autowired ReservationRepository reservations;
  @Autowired ObjectMapper json;

  @BeforeEach
  void clean() {
    reservations.deleteAll();
    remoteStatus = 200;
    active = true;
    calls.set(0);
  }

  String payload() {
    return "{\"roomId\":\""
        + ROOM
        + "\",\"studentId\":\""
        + OWNER
        + "\",\"date\":\""
        + DATE
        + "\",\"startTime\":\"13:00\",\"endTime\":\"15:00\",\"purpose\":\"Diskusi"
        + " penelitian\",\"participantCount\":6}";
  }

  org.springframework.test.web.servlet.ResultActions postBody(String body) throws Exception {
    return mvc.perform(
        post("/api/v1/reservations")
            .with(httpBasic(OWNER, "test-student-password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  Reservation fixture(String owner, ReservationStatus status, String start, String end) {
    var r = new Reservation();
    r.roomId = ROOM;
    r.studentId = owner;
    r.date = DATE;
    r.startTime = LocalTime.parse(start);
    r.endTime = LocalTime.parse(end);
    r.purpose = "Diskusi simulasi";
    r.participantCount = 2;
    r.status = status;
    r.createdAt = OffsetDateTime.now();
    return reservations.saveAndFlush(r);
  }

  org.springframework.test.web.servlet.ResultActions available(String start, String end)
      throws Exception {
    return mvc.perform(
        get("/api/v1/availability")
            .param("roomId", ROOM.toString())
            .param("date", DATE.toString())
            .param("startTime", start)
            .param("endTime", end));
  }

  @Test
  void createCallsRoomOverHttpAndPersistsPending() throws Exception {
    var result =
        postBody(payload())
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.startTime").value("13:00"))
            .andReturn();
    var body = json.readTree(result.getResponse().getContentAsString());
    UUID.fromString(body.get("id").asText());
    OffsetDateTime.parse(body.get("createdAt").asText());
    Assertions.assertEquals(1, calls.get());
    Assertions.assertEquals(1, reservations.count());
    mvc.perform(
            get("/api/v1/reservations/" + body.get("id").asText())
                .with(user(OWNER).roles("STUDENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roomId").value(ROOM.toString()))
        .andExpect(jsonPath("$.createdAt").value(body.get("createdAt").asText()));
  }

  @Test
  void pendingDoesNotBlockMultipleRequests() throws Exception {
    postBody(payload()).andExpect(status().isCreated());
    postBody(payload()).andExpect(status().isCreated());
    available("13:00", "15:00").andExpect(jsonPath("$.available").value(true));
  }

  @Test
  void approvedOverlapsButTouchingBoundariesDoNot() throws Exception {
    fixture(OWNER, ReservationStatus.APPROVED, "13:00", "15:00");
    for (var slot :
        List.of(
            new String[] {"13:00", "15:00"},
            new String[] {"12:00", "14:00"},
            new String[] {"14:00", "16:00"},
            new String[] {"12:00", "16:00"},
            new String[] {"13:30", "14:30"}))
      available(slot[0], slot[1]).andExpect(jsonPath("$.available").value(false));
    available("12:00", "13:00").andExpect(jsonPath("$.available").value(true));
    available("15:00", "16:00").andExpect(jsonPath("$.available").value(true));
    postBody(payload())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));
    Assertions.assertEquals(1, reservations.count());
  }

  @Test
  void inactiveAndCapacityRejected() throws Exception {
    active = false;
    postBody(payload())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ROOM_INACTIVE"));
    active = true;
    postBody(payload().replace("\"participantCount\":6", "\"participantCount\":9"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CAPACITY_EXCEEDED"));
    Assertions.assertEquals(0, reservations.count());
  }

  @Test
  void invalidRequestsRejectedBeforePersistence() throws Exception {
    for (var body :
        List.of(
            "{}",
            payload().replace("13:00", "15:00"),
            payload().replace("13:00", "16:00"),
            payload().replace("13:00", "13:00:00"),
            payload().replace("13:00", "25:00"),
            payload().replace(DATE.toString(), "2000-01-01"),
            payload().replace("\"participantCount\":6", "\"participantCount\":0"),
            payload().replace("Diskusi penelitian", "AB"),
            payload().replace(ROOM.toString(), "invalid")))
      postBody(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    available("bad", "15:00").andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/availability")).andExpect(status().isBadRequest());
    Assertions.assertEquals(0, reservations.count());
  }

  @Test
  void upstreamMissingAndFailureAreMapped() throws Exception {
    remoteStatus = 404;
    postBody(payload()).andExpect(status().isNotFound());
    available("13:00", "15:00").andExpect(status().isNotFound());
    remoteStatus = 500;
    postBody(payload())
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("ROOM_SERVICE_UNAVAILABLE"));
    available("13:00", "15:00").andExpect(status().isServiceUnavailable());
    Assertions.assertEquals(0, reservations.count());
  }

  @Test
  void studentCannotImpersonateOrReadOthers() throws Exception {
    var other = fixture("other-student", ReservationStatus.PENDING, "13:00", "15:00");
    fixture(OWNER, ReservationStatus.PENDING, "13:00", "15:00");
    postBody(payload().replace(OWNER, "other-student")).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/reservations").with(user(OWNER).roles("STUDENT")))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].studentId").value(OWNER));
    mvc.perform(
            get("/api/v1/reservations")
                .param("studentId", "other-student")
                .with(user(OWNER).roles("STUDENT")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/reservations")
                .param("roomId", ROOM.toString())
                .with(user(OWNER).roles("STUDENT")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/reservations/" + other.id).with(user(OWNER).roles("STUDENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminListsAndFiltersAllAndDetailMissing404() throws Exception {
    var r = fixture("other-student", ReservationStatus.APPROVED, "13:00", "15:00");
    fixture(OWNER, ReservationStatus.PENDING, "13:00", "15:00");
    mvc.perform(get("/api/v1/reservations").with(user("admin").roles("ADMIN")))
        .andExpect(jsonPath("$.total").value(2));
    mvc.perform(
            get("/api/v1/reservations")
                .param("status", "APPROVED")
                .param("studentId", "other-student")
                .param("roomId", ROOM.toString())
                .with(user("admin").roles("ADMIN")))
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(get("/api/v1/reservations/" + r.id).with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/reservations/" + UUID.randomUUID()).with(user("admin").roles("ADMIN")))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/reservations")
                .param("status", "INVALID")
                .with(user("admin").roles("ADMIN")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void protectedEndpointsRequireAuthentication() throws Exception {
    mvc.perform(get("/api/v1/reservations"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
    mvc.perform(
            post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(payload()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void swaggerAccessible() throws Exception {
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }
}
