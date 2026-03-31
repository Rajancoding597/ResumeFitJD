package com.rajan.resumetailor.service;

import java.util.List;

public interface ResumeRewriterClient {

    List<RewriteSuggestion> rewriteBullets(RewriteBulletsRequest request);
}
