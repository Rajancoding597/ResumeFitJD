package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LatexResumeFixturesTest {

    private final LatexResumeService service = new LatexResumeService();

    @Test
    void itemLabelIsNotIncludedInBulletText() throws Exception {
        String latex = read("latex-fixtures/item-labels.tex");
        var parsed = service.parse(latex);
        assertEquals(2, parsed.bullets().size());
        assertTrue(parsed.bullets().get(0).originalBullet().startsWith("Led migration"));
        assertTrue(parsed.bullets().get(1).originalBullet().startsWith("Built CI/CD"));
        assertTrue(latex.contains("\\item[--]"));
    }

    @Test
    void extractsNestedItemsInNestedItemize() throws Exception {
        String latex = read("latex-fixtures/nested-itemize.tex");
        var parsed = service.parse(latex);
        assertEquals(4, parsed.bullets().size());
        assertTrue(parsed.bullets().get(1).originalBullet().startsWith("Owned observability"));
        assertTrue(parsed.bullets().get(2).originalBullet().startsWith("Added tracing"));
    }

    private String read(String classpathPath) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(classpathPath)) {
            assertTrue(in != null, "Missing test fixture: " + classpathPath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
