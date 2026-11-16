package id.ac.ub.filkom.room.controller;

import id.ac.ub.filkom.room.dto.ItemList;
import id.ac.ub.filkom.room.dto.RoomResponse;
import id.ac.ub.filkom.room.dto.CreateRoomV2Request;
import id.ac.ub.filkom.room.service.RoomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/rooms")
public class RoomV2Controller {
  private final RoomService rooms;

  public RoomV2Controller(RoomService rooms) {
    this.rooms = rooms;
  }

  @GetMapping
  public ItemList<RoomResponse> list(
      @RequestParam(required = false) @Min(1) Integer minCapacity,
      @RequestParam(required = false) String building,
      @RequestParam(required = false) String facility) {
    return rooms.list(minCapacity, building, facility);
  }

  @GetMapping("/{roomId}")
  public RoomResponse get(@PathVariable UUID roomId) {
    return rooms.get(roomId);
  }

  @PostMapping
  public ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomV2Request request) {
    var room = rooms.create(request.toCreateRoomRequest());
    return ResponseEntity.created(URI.create("/api/v2/rooms/" + room.id())).body(room);
  }
}
