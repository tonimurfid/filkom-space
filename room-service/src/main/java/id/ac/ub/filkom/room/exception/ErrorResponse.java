package id.ac.ub.filkom.room.exception;

public record ErrorResponse(int status, String code, String message, String path) {}
