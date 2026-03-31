package com.rajan.resumetailor.dto;

import java.util.List;

public record AtsInsights(
        int overallScore,
        List<AtsKeyword> mustHave,
        List<AtsKeyword> niceToHave,
        List<BulletHeat> bulletHeatmap,
        List<String> notes
) {
}

