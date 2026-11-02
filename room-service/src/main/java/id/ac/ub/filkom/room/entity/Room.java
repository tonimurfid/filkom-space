package id.ac.ub.filkom.room.entity;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "rooms")
public class Room {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @Column(nullable = false, unique = true, length = 30)
  public String code;

  @Column(nullable = false, length = 100)
  public String name;

  @Column(nullable = false, columnDefinition = "text")
  public String building;

  @Column(nullable = false)
  public int floor;

  @Column(nullable = false)
  public int capacity;

  @ElementCollection
  @CollectionTable(name = "room_facilities", joinColumns = @JoinColumn(name = "room_id"))
  @OrderColumn(name = "position")
  @Column(name = "facility", nullable = false, columnDefinition = "text")
  public List<String> facilities = new ArrayList<>();

  @Column(nullable = false)
  public boolean isActive = true;

  @Column(nullable = false, columnDefinition = "boolean default false")
  public boolean archived = false;
}
