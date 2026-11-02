package id.ac.ub.filkom.room.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record ReplaceRoomRequest(
    @NotBlank @Size(min = 3, max = 30) String code,
    @NotBlank @Size(min = 3, max = 100) String name,
    @NotBlank String building,
    @NotNull @Min(1) Integer floor,
    @NotNull @Min(1) Integer capacity,
    @NotNull List<@NotNull String> facilities) {}
