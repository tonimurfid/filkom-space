package id.ac.ub.filkom.reservation.client;

import id.ac.ub.filkom.reservation.exception.ApiException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class RoomClient {
  private final RestClient client;

  public record RoomInfo(UUID id, int capacity, boolean isActive) {}

  public RoomClient(@Value("${app.room-service-url}") String url) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(2));
    factory.setReadTimeout(Duration.ofSeconds(3));
    client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
  }

  public RoomInfo get(UUID id) {
    try {
      var room = client.get().uri("/api/v1/rooms/{id}", id).retrieve().body(RoomInfo.class);
      if (room == null || !id.equals(room.id()) || room.capacity() < 1)
        throw new ApiException(
            503, "ROOM_SERVICE_UNAVAILABLE", "Respons Room Service tidak valid.");
      return room;
    } catch (HttpClientErrorException.NotFound e) {
      throw new ApiException(404, "ROOM_NOT_FOUND", "Ruangan tidak ditemukan.");
    } catch (RestClientException e) {
      throw new ApiException(
          503, "ROOM_SERVICE_UNAVAILABLE", "Room Service sementara tidak tersedia.");
    }
  }
}
