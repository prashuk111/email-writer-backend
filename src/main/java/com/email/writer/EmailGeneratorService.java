package com.email.writer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
@Service
public class EmailGeneratorService {

    private final WebClient webClient;
    private final String apiKey;

    public EmailGeneratorService(
                                 @Value("${gemini.api.url}") String baseUrl,
                                 @Value("${gemini.api.key}") String geminiApiKey) {
        this.apiKey = geminiApiKey;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String generateEmailReply(EmailRequest emailRequest) {

        // Build Prompt
        String prompt = buildPrompt(emailRequest);
        // Prepare Raw Json Body

        Map<String, Object> requestBody = Map.of(
                "model", "gemini-3.6-flash",
                "input", prompt
        );

        // Send Request - In this we trigger request and take response in response variable
        String response = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/interactions")
                        .build())
                .header("x-goog-api-key",apiKey)
                .header("Content-Type","application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        // Extract Response
        return  extractResponseContent(response);
    }

    private String extractResponseContent(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            for (JsonNode step : root.path("steps")) {

                if ("model_output".equals(step.path("type").asText())) {

                    JsonNode content = step.path("content");

                    if (content.isArray() && !content.isEmpty()) {
                        return content.get(0)
                                .path("text").asString();
                    }
                }
            }

            throw new RuntimeException("No model output found in Gemini response");

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response", e);
        }
    }


    private String buildPrompt(EmailRequest emailRequest) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
        Generate a professional email reply to the following email.
        Return only the email reply.
        Do not provide multiple options.
        Do not explain your answer.
        Do not include suggestions or commentary.
        """);
                if(emailRequest.getTone() != null && !emailRequest.getTone().isEmpty()){
                    prompt.append("Use a").append(emailRequest.getTone()).append(" Tone.");
                    // Use a Professional Tone or what tone we will choose
                }
                prompt.append("Original Email : \n").append(emailRequest.getEmailContent());
                return prompt.toString();
    }
}
