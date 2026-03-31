package com.rajan.resumetailor.service;

import java.util.List;

public record RewriteBulletsRequest(
        List<String> originalBullets,
        String jobDescription,
        List<String> allowedKeywords
) {
}

