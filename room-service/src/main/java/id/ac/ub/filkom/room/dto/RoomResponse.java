package id.ac.ub.filkom.room.dto;

import id.ac.ub.filkom.room.entity.Room;
import java.util.*;

public record RoomResponse(
    UUID id,
    String code,
    String name,
    String building,
    int floor,
    int capacity,
    List<String> facilities,
    boolean isActive) {
  public static RoomResponse from(Room r) {
    return new RoomResponse(
        r.id,
        r.code,
        r.name,
        r.building,
        r.floor,
        r.capacity,
        List.copyOf(r.facilities),
        r.isActive);
  }
}
