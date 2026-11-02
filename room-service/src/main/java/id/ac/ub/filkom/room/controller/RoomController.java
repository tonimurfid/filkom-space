package id.ac.ub.filkom.room.controller;

import id.ac.ub.filkom.room.dto.*;
import id.ac.ub.filkom.room.service.RoomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {
  private final RoomService rooms;

  public RoomController(RoomService rooms) {
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

  @PutMapping("/{roomId}")
  public RoomResponse replace(@PathVariable UUID roomId, @Valid @RequestBody ReplaceRoomRequest req) {
    return rooms.replace(roomId, req);
  }

  @PatchMapping("/{roomId}/status")
  public RoomResponse changeStatus(@PathVariable UUID roomId, @Valid @RequestBody UpdateRoomStatusRequest req) {
    return rooms.changeStatus(roomId, req);
  }

  @DeleteMapping("/{roomId}")
  public ResponseEntity<Void> archive(@PathVariable UUID roomId) {
    rooms.archive(roomId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping
  public ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest req) {
    var r = rooms.create(req);
    return ResponseEntity.created(URI.create("/api/v1/rooms/" + r.id())).body(r);
  }
}
