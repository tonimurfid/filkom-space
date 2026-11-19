package id.ac.ub.filkom.room.service;

import id.ac.ub.filkom.room.dto.*;
import id.ac.ub.filkom.room.client.RoomReservationClient;
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
  private final RoomReservationClient reservations;

  public RoomService(RoomRepository rooms, RoomReservationClient reservations) {
    this.rooms = rooms;
    this.reservations = reservations;
  }

  private Room existing(UUID id) {
    var room = rooms.findById(id).orElseThrow(() ->
        new ApiException(404, "ROOM_NOT_FOUND", "Ruangan tidak ditemukan."));
    if (room.archived)
      throw new ApiException(404, "ROOM_NOT_FOUND", "Ruangan tidak ditemukan.");
    return room;
  }

  @Transactional(readOnly = true)
  public ItemList<RoomResponse> list(Integer min, String building, String facility) {
    Specification<Room> filter =
        (r, q, b) -> {
          var p = new ArrayList<Predicate>();
          p.add(b.isFalse(r.get("archived")));
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
    return RoomResponse.from(existing(id));
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

  @Transactional
  public RoomResponse replace(UUID id, ReplaceRoomRequest req) {
    var room = existing(id);
    if (!room.code.equals(req.code()) && rooms.existsByCode(req.code())) throw duplicate();
    room.code = req.code();
    room.name = req.name();
    room.building = req.building();
    room.floor = req.floor();
    room.capacity = req.capacity();
    room.facilities.clear();
    room.facilities.addAll(req.facilities());
    try {
      return RoomResponse.from(rooms.saveAndFlush(room));
    } catch (DataIntegrityViolationException e) {
      throw duplicate();
    }
  }

  @Transactional
  public RoomResponse replaceV2(UUID id, ReplaceRoomV2Request request) {
    replace(id, request.toReplacement());
    return changeStatus(id, new UpdateRoomStatusRequest(request.isActive()));
  }

  @Transactional
  public RoomResponse changeStatus(UUID id, UpdateRoomStatusRequest req) {
    var room = existing(id);
    room.isActive = req.isActive();
    return RoomResponse.from(rooms.saveAndFlush(room));
  }

  @Transactional
  public void archive(UUID id) {
    var room = rooms.findById(id).orElseThrow(() ->
        new ApiException(404, "ROOM_NOT_FOUND", "Ruangan tidak ditemukan."));
    if (room.archived) return;
    if (reservations.hasFutureBookings(id))
      throw new ApiException(409, "ROOM_HAS_RESERVATIONS",
          "Ruangan masih memiliki reservasi mendatang.");
    room.archived = true;
    room.isActive = false;
    rooms.saveAndFlush(room);
  }

  private ApiException duplicate() {
    return new ApiException(409, "ROOM_CODE_CONFLICT", "Kode ruangan sudah digunakan.");
  }
}
