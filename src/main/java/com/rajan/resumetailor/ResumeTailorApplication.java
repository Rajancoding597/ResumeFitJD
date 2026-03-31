package com.rajan.resumetailor;

import com.rajan.resumetailor.config.GeminiProperties;
import com.rajan.resumetailor.config.AiExecutionProperties;
import com.rajan.resumetailor.config.AiPromptLimitsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GeminiProperties.class, AiExecutionProperties.class, AiPromptLimitsProperties.class})
public class ResumeTailorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeTailorApplication.class, args);
    }
}
