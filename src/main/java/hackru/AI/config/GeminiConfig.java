package hackru.AI.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class GeminiConfig {
    
    @Value("${gemini.model:gemini-2.5-flash}")
    private String modelName;

    @Bean
    public Client geminiClient() {
        // Check both GEMINI_API_KEY (user preference) and GOOGLE_API_KEY (library default)
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GOOGLE_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.error("GEMINI_API_KEY or GOOGLE_API_KEY environment variable not set. Cannot initialize Gemini Client.");
            throw new IllegalStateException("GEMINI_API_KEY or GOOGLE_API_KEY environment variable must be set");
        }
        log.info("Initializing Gemini Client with API key (length: {})", apiKey.length());
        
        try {
            // Use Client.builder().apiKey() to pass the API key directly
            Client client = Client.builder().apiKey(apiKey).build();
            log.info("✅ Gemini Client initialized successfully");
            return client;
        } catch (Exception e) {
            log.error("❌ Failed to create Gemini Client", e);
            throw new RuntimeException("Failed to initialize Gemini Client. Check GEMINI_API_KEY or GOOGLE_API_KEY environment variable.", e);
        }
    }
    
    public String getModelName() {
        return modelName;
    }
}
