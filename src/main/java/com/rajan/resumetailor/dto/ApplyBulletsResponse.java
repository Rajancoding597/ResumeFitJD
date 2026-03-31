package com.rajan.resumetailor.dto;

import java.util.List;

public record ApplyBulletsResponse(
        String updatedLatex,
        List<String> warnings
) {
}

