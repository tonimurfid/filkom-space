package id.ac.ub.filkom.room.config;

import id.ac.ub.filkom.room.dto.CreateRoomRequest;
import id.ac.ub.filkom.room.repository.RoomRepository;
import id.ac.ub.filkom.room.service.RoomService;
import java.util.List;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty(name = "app.seed-data", havingValue = "true")
public class DemoData {
  @Bean
  ApplicationRunner seed(RoomRepository repo, RoomService service) {
    return args -> {
      if (!repo.existsByCode("SIM-DISKUSI-01"))
        service.create(
            new CreateRoomRequest(
                "SIM-DISKUSI-01",
                "Ruang Diskusi A (simulasi)",
                "Gedung G (simulasi)",
                2,
                8,
                List.of("PROYEKTOR", "PAPAN_TULIS")));
      if (!repo.existsByCode("SIM-RAPAT-01"))
        service.create(
            new CreateRoomRequest(
                "SIM-RAPAT-01",
                "Ruang Rapat B (simulasi)",
                "Gedung F (simulasi)",
                1,
                20,
                List.of("PROYEKTOR", "WIFI")));
    };
  }
}
