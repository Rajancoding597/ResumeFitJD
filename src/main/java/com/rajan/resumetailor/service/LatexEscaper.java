package com.rajan.resumetailor.service;

import org.springframework.stereotype.Component;

@Component
public class LatexEscaper {

    public String escapeBulletText(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }

        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            char previous = index > 0 ? value.charAt(index - 1) : '\0';
            boolean alreadyEscaped = previous == '\\';

            if (!alreadyEscaped && (current == '%' || current == '&' || current == '_' || current == '#' || current == '$')) {
                escaped.append('\\');
            }

            escaped.append(current);
        }
        return escaped.toString();
    }
}

