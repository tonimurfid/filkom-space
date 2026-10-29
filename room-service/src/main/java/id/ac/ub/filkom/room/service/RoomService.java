package id.ac.ub.filkom.room.service;

import id.ac.ub.filkom.room.dto.*;
import id.ac.ub.filkom.room.entity.Room;
import id.ac.ub.filkom.room.exception.ApiException;
import id.ac.ub.filkom.room.repository.RoomRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {
  private final RoomRepository rooms;

  public RoomService(RoomRepository rooms) {
    this.rooms = rooms;
  }

  @Transactional(readOnly = true)
  public ItemList<RoomResponse> list(Integer min, String building, String facility) {
    Specification<Room> filter =
        (r, q, b) -> {
          var p = new ArrayList<Predicate>();
          if (min != null) p.add(b.ge(r.get("capacity"), min));
          if (building != null) p.add(b.equal(r.get("building"), building));
          if (facility != null) p.add(b.isMember(facility, r.get("facilities")));
          return b.and(p.toArray(Predicate[]::new));
        };
    return ItemList.of(
        rooms.findAll(filter, Sort.by("code")).stream().map(RoomResponse::from).toList());
  }

  @Transactional(readOnly = true)
  public RoomResponse get(UUID id) {
    return RoomResponse.from(
        rooms
            .findById(id)
            .orElseThrow(
                () -> new ApiException(404, "ROOM_NOT_FOUND", "Ruangan tidak ditemukan.")));
  }

  @Transactional
  public RoomResponse create(CreateRoomRequest req) {
    if (rooms.existsByCode(req.code())) throw duplicate();
    var r = new Room();
    r.code = req.code();
    r.name = req.name();
    r.building = req.building();
    r.floor = req.floor();
    r.capacity = req.capacity();
    r.facilities = new ArrayList<>(req.facilities());
    try {
      return RoomResponse.from(rooms.saveAndFlush(r));
    } catch (DataIntegrityViolationException e) {
      throw duplicate();
    }
  }

  private ApiException duplicate() {
    return new ApiException(409, "ROOM_CODE_CONFLICT", "Kode ruangan sudah digunakan.");
  }
}
