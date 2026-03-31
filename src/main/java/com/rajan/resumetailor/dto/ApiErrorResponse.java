package com.rajan.resumetailor.dto;

import java.util.List;

public record ApiErrorResponse(String message, List<String> details, String requestId) {
}
