package id.ac.ub.filkom.room.dto;

import java.util.List;

public record ItemList<T>(List<T> items, int total) {
  public static <T> ItemList<T> of(List<T> items) {
    return new ItemList<>(items, items.size());
  }
}
