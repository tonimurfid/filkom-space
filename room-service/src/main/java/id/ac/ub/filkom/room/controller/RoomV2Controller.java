package id.ac.ub.filkom.room.controller;

import id.ac.ub.filkom.room.dto.ItemList;
import id.ac.ub.filkom.room.dto.RoomResponse;
import id.ac.ub.filkom.room.dto.*;
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

  @PutMapping("/{roomId}")
  public RoomResponse replace(
      @PathVariable UUID roomId, @Valid @RequestBody ReplaceRoomV2Request request) {
    return rooms.replaceV2(roomId, request);
  }

  @PatchMapping("/{roomId}/status")
  public RoomResponse changeStatus(
      @PathVariable UUID roomId, @Valid @RequestBody UpdateRoomV2StatusRequest request) {
    return rooms.changeStatus(roomId, new UpdateRoomStatusRequest(request.isActive()));
  }

  @DeleteMapping("/{roomId}")
  public ResponseEntity<Void> archive(@PathVariable UUID roomId) {
    rooms.archive(roomId);
    return ResponseEntity.noContent().build();
  }
}
