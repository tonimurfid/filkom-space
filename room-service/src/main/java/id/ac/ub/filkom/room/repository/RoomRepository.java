package id.ac.ub.filkom.room.repository;

import id.ac.ub.filkom.room.entity.Room;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface RoomRepository extends JpaRepository<Room, UUID>, JpaSpecificationExecutor<Room> {
  boolean existsByCode(String code);
}
