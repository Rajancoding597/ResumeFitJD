package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LatexPlainTextExtractorTest {

    private final LatexPlainTextExtractor extractor = new LatexPlainTextExtractor();

    @Test
    void extractsSearchableTextAndKeepsEscapedSymbols() {
        String latex = """
                \\section{Experience}
                \\begin{itemize}
                \\item Improved performance by 5\\% using Java and SQL.
                \\end{itemize}
                % comment should be dropped
                """;

        String text = extractor.extract(latex);
        assertTrue(text.contains("improved performance"));
        assertTrue(text.contains("5%"));
        assertTrue(text.contains("java"));
        assertTrue(text.contains("sql"));
        assertTrue(!text.contains("comment should be dropped"));
    }
}

