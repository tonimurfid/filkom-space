package id.ac.ub.filkom.room.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateRoomStatusRequest(@NotNull Boolean isActive) {}
