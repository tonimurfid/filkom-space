package id.ac.ub.filkom.room.client;

import id.ac.ub.filkom.room.exception.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class RoomReservationClient {
  private final RestClient client;

  // Only for local academic demonstration. Production needs a dedicated service identity.
  public RoomReservationClient(
      @Value("${app.reservation-service-url}") String baseUrl,
      @Value("${app.admin-password}") String adminPassword) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(2));
    factory.setReadTimeout(Duration.ofSeconds(3));
    this.client = RestClient.builder().baseUrl(baseUrl)
        .defaultHeaders(h -> h.setBasicAuth("admin", adminPassword))
        .requestFactory(factory).build();
  }

  public record ReservationSummary(LocalDate date, String endTime, String status) {}
  public record ReservationList(List<ReservationSummary> items, int total) {}

  public boolean hasFutureBookings(UUID roomId) {
    try {
      var list = client.get().uri("/api/v1/reservations?roomId={roomId}", roomId)
          .retrieve().body(ReservationList.class);
      if (list == null || list.items() == null)
        throw new ApiException(503, "RESERVATION_SERVICE_UNAVAILABLE",
            "Respons Reservation Service tidak valid.");
      var now = LocalDateTime.now(ZoneId.of("Asia/Jakarta"));
      for (var r : list.items()) {
        if (r == null || r.date() == null || r.endTime() == null || r.status() == null)
          throw new ApiException(503, "RESERVATION_SERVICE_UNAVAILABLE",
              "Respons Reservation Service tidak valid.");
        if (("PENDING".equals(r.status()) || "APPROVED".equals(r.status()))
            && r.date().atTime(LocalTime.parse(r.endTime())).isAfter(now)) return true;
      }
      return false;
    } catch (RestClientException | java.time.format.DateTimeParseException e) {
      throw new ApiException(503, "RESERVATION_SERVICE_UNAVAILABLE",
          "Reservation Service sementara tidak tersedia.");
    }
  }
}
