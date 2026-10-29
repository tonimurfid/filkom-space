package id.ac.ub.filkom.reservation.exception;

public record ErrorResponse(int status, String code, String message, String path) {}
