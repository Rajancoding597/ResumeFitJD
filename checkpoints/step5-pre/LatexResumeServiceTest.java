package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rajan.resumetailor.service.LatexResumeService.ParsedResume;
import java.util.List;
import org.junit.jupiter.api.Test;

class LatexResumeServiceTest {

    private final LatexResumeService service = new LatexResumeService();

    @Test
    void parsesItemBlocksAndMergesWithoutTouchingOtherLatex() {
        String latex = """
                \\section{Experience}
                \\begin{itemize}
                  \\item Built internal dashboards for delivery metrics.
                  \\item Automated weekly reports for leadership.
                \\end{itemize}
                """;

        ParsedResume parsed = service.parse(latex);

        assertEquals(2, parsed.bullets().size());
        assertEquals("Built internal dashboards for delivery metrics.", parsed.bullets().get(0).originalBullet());

        String merged = service.merge(parsed, List.of(
                "Built internal dashboards that surfaced delivery trends for engineering leaders.",
                "Automated weekly reporting workflows for leadership visibility."
        ));

        assertTrue(merged.contains("\\section{Experience}"));
        assertTrue(merged.contains("\\end{itemize}"));
        assertTrue(merged.contains("Automated weekly reporting workflows for leadership visibility."));
    }

    @Test
    void parsesResumeItemMacros() {
        String latex = """
                \\resumeItem{Built a Java utility for log analysis}
                \\resumeItem{Improved error triage across the team}
                """;

        ParsedResume parsed = service.parse(latex);

        assertEquals(2, parsed.bullets().size());
        assertEquals("Built a Java utility for log analysis", parsed.bullets().get(0).originalBullet());
    }

    @Test
    void stopsBulletAtCustomEnvironmentEnd() {
        String latex = """
                \\begin{highlights}
                \\item Integrated features into CI/CD pipelines, increasing deployment consistency and reducing release issues by 10\\%.
                \\end{highlights}
                \\end{onecolentry}
                                
                \\vspace{0.3 cm}
                \\noindent
                """;

        ParsedResume parsed = service.parse(latex);

        assertEquals(1, parsed.bullets().size());
        assertEquals(
                "Integrated features into CI/CD pipelines, increasing deployment consistency and reducing release issues by 10\\%.",
                parsed.bullets().get(0).originalBullet()
        );
    }

    @Test
    void excludesTrailingLayoutCommandsFromBulletContent() {
        String latex = """
                \\begin{highlights}
                \\item Built internal tooling for release coordination.
                \\par
                % spacing note
                \\end{highlights}
                """;

        ParsedResume parsed = service.parse(latex);

        assertEquals(1, parsed.bullets().size());
        assertEquals("Built internal tooling for release coordination.", parsed.bullets().get(0).originalBullet());

        String merged = service.merge(parsed, List.of("Built internal tooling that improved release coordination."));
        assertTrue(merged.contains("\\par"));
        assertTrue(merged.contains("% spacing note"));
    }
}
