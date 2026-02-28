package com.healthcare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.dto.ChatResponse;
import com.healthcare.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for AI integration — supports Ollama (local) and OpenAI
 */
@Service
@Slf4j
public class OpenAIService {

    // OpenAI fallback
    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.model}")
    private String openAiModel;

    // Ollama config
    @Value("${ollama.enabled:false}")
    private boolean ollamaEnabled;

    @Value("${ollama.base.url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3:latest}")
    private String ollamaModel;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Emergency keywords for triage
    private static final String[] EMERGENCY_KEYWORDS = {
        "chest pain", "heart attack", "can't breathe", "difficulty breathing",
        "severe bleeding", "suicide", "suicidal", "stroke", "unconscious",
        "severe headache", "can't move", "paralysis", "seizure"
    };

    public OpenAIService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Public entry for LocationChatService — calls Ollama or OpenAI
     */
    public String callOllama(List<Map<String, String>> messages) throws Exception {
        return callOpenAI(messages, 400);
    }

    /**
     * Check if message contains emergency keywords
     */
    public boolean isEmergency(String message) {
        String lowerMessage = message.toLowerCase();
        for (String keyword : EMERGENCY_KEYWORDS) {
            if (lowerMessage.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generate emergency response
     */
    public ChatResponse generateEmergencyResponse() {
        ChatResponse response = new ChatResponse();
        response.setIsEmergency(true);
        response.setResponse("⚠️ EMERGENCY ALERT ⚠️\n\n"
                + "Your message contains keywords indicating a potential medical emergency.\n\n"
                + "IMMEDIATE ACTIONS:\n"
                + "🚨 Call emergency services (911) immediately\n"
                + "🏥 Go to the nearest emergency room\n"
                + "📞 Contact your doctor or healthcare provider\n\n"
                + "Do not rely on this chat for emergency medical situations. "
                + "This is an AI assistant and cannot provide emergency medical care.");

        ChatResponse.StructuredResponse structured = new ChatResponse.StructuredResponse();
        structured.setSeverityLevel("CRITICAL - EMERGENCY");
        structured.setRecommendedAction("Seek immediate medical attention by calling 911");
        structured.setDisclaimer("This is not a substitute for professional medical advice");
        response.setStructuredResponse(structured);

        return response;
    }

    /**
     * Generate AI response with conversation context
     */
    public ChatResponse generateChatResponse(String userMessage, List<Message> conversationHistory, String healthContext) {
        try {
            // Build conversation messages
            List<Map<String, String>> messages = new ArrayList<>();

            // System prompt with health context
            String systemPrompt = buildSystemPrompt(healthContext);
            messages.add(Map.of("role", "system", "content", systemPrompt));

            // Add conversation history (last 10 messages for context)
            int startIndex = Math.max(0, conversationHistory.size() - 10);
            for (int i = startIndex; i < conversationHistory.size(); i++) {
                Message msg = conversationHistory.get(i);
                messages.add(Map.of(
                        "role", msg.getRole() == Message.MessageRole.USER ? "user" : "assistant",
                        "content", msg.getContent()
                ));
            }

            // Add current user message
            messages.add(Map.of("role", "user", "content", userMessage));

            // Call OpenAI API
            String apiResponse = callOpenAI(messages);

            // Parse and structure response
            return parseStructuredResponse(apiResponse);

        } catch (Exception e) {
            log.error("Error generating AI response", e);
            return createErrorResponse();
        }
    }

    /**
     * Generate explanation for OCR extracted text
     */
    public String generateReportExplanation(String extractedText) {
        try {
            if (extractedText == null || extractedText.isBlank()
                    || extractedText.contains("No text could be extracted")) {
                return "The report could not be read clearly. Please upload a clearer image or PDF.";
            }

            String prompt = "Medical report text:\n" + extractedText + "\n\n"
                    + "In 3-5 sentences: summarize the key findings, flag any abnormal values, "
                    + "and suggest one next step for the patient. Use simple non-technical language.";

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content",
                            "You are a friendly doctor summarizing lab results for a patient. Be concise."),
                    Map.of("role", "user", "content", prompt)
            );

            return callOpenAI(messages, 300);

        } catch (Exception e) {
            log.error("Error generating report explanation", e);
            return "Unable to generate explanation at this time.";
        }
    }

    /**
     * Generate SOAP note draft for doctors
     */
    public String generateSoapNote(String patientHistory, String symptoms) {
        try {
            String prompt = "Generate a SOAP note draft based on:\n\n"
                    + "Patient History: " + patientHistory + "\n"
                    + "Current Symptoms: " + symptoms + "\n\n"
                    + "Provide structured SOAP format:\n"
                    + "S (Subjective):\n"
                    + "O (Objective):\n"
                    + "A (Assessment):\n"
                    + "P (Plan):";

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", "You are a medical professional creating clinical notes."),
                    Map.of("role", "user", "content", prompt)
            );

            return callOpenAI(messages);

        } catch (Exception e) {
            log.error("Error generating SOAP note", e);
            return "Unable to generate SOAP note.";
        }
    }

    /**
     * Build system prompt with health context — requests bilingual JSON output
     */
    private String buildSystemPrompt(String healthContext) {
        return "You are a knowledgeable medical AI assistant.\n\n"
                + "RULES:\n"
                + "- Provide helpful, empathetic health information in clear English (under 120 words).\n"
                + "- Then translate the SAME advice into simple Telugu.\n"
                + "- Always remind users to consult a doctor for diagnosis.\n"
                + (healthContext != null && !healthContext.isEmpty()
                ? "- Patient context: " + healthContext + "\n" : "")
                + "\nReturn ONLY this exact JSON (no extra text, no code fences):\n"
                + "{\"english\":\"English advice here\",\"telugu\":\"Telugu translation here\"}";
    }

    /**
     * Call AI API — routes to Ollama (local) or OpenAI based on config
     */
    private String callOpenAI(List<Map<String, String>> messages) throws Exception {
        return callOpenAI(messages, 600);
    }

    private String callOpenAI(List<Map<String, String>> messages, int maxTokens) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String apiUrl;
        String modelName;

        if (ollamaEnabled) {
            // Ollama OpenAI-compatible endpoint — no auth needed
            apiUrl = ollamaBaseUrl + "/v1/chat/completions";
            modelName = ollamaModel;
            log.debug("Using Ollama at {} with model {}", apiUrl, modelName);
        } else {
            // OpenAI
            apiUrl = OPENAI_API_URL;
            modelName = openAiModel;
            headers.setBearerAuth(apiKey);
            log.debug("Using OpenAI with model {}", modelName);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", maxTokens);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                request,
                String.class
        );

        JsonNode jsonResponse = objectMapper.readTree(response.getBody());
        return jsonResponse.get("choices").get(0).get("message").get("content").asText();
    }

    /**
     * Parse AI response into structured format — handles bilingual JSON
     */
    private ChatResponse parseStructuredResponse(String aiResponse) {
        ChatResponse response = new ChatResponse();
        response.setIsEmergency(false);

        // Try to parse bilingual JSON {"english":"...","telugu":"..."}
        try {
            String cleaned = aiResponse.replaceAll("```[a-zA-Z]*\\n?", "").replace("```", "").trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                JsonNode node = objectMapper.readTree(cleaned.substring(start, end + 1));
                if (node.has("english")) {
                    response.setResponse(node.get("english").asText());
                    response.setResponseTelugu(node.has("telugu") ? node.get("telugu").asText() : null);
                    ChatResponse.StructuredResponse structured = new ChatResponse.StructuredResponse();
                    structured.setDisclaimer("This information is for educational purposes only and is not a substitute for professional medical advice.");
                    response.setStructuredResponse(structured);
                    return response;
                }
            }
        } catch (Exception ignored) {
            /* fall through to plain-text handling */ }

        response.setResponse(aiResponse);

        // Plain-text section parsing fallback
        ChatResponse.StructuredResponse structured = new ChatResponse.StructuredResponse();
        if (aiResponse.contains("Symptom Summary") || aiResponse.contains("Possible Causes")) {
            String[] lines = aiResponse.split("\n");
            StringBuilder summary = new StringBuilder();
            StringBuilder causes = new StringBuilder();
            StringBuilder severity = new StringBuilder();
            StringBuilder action = new StringBuilder();
            String currentSection = "";
            for (String line : lines) {
                if (line.toLowerCase().contains("symptom")) {
                    currentSection = "summary";
                } else if (line.toLowerCase().contains("causes")) {
                    currentSection = "causes";
                } else if (line.toLowerCase().contains("severity")) {
                    currentSection = "severity";
                } else if (line.toLowerCase().contains("action") || line.toLowerCase().contains("recommend")) {
                    currentSection = "action";
                } else {
                    switch (currentSection) {
                        case "summary" ->
                            summary.append(line).append("\n");
                        case "causes" ->
                            causes.append(line).append("\n");
                        case "severity" ->
                            severity.append(line).append("\n");
                        case "action" ->
                            action.append(line).append("\n");
                    }
                }
            }
            structured.setSymptomSummary(summary.toString().trim());
            structured.setPossibleCauses(causes.toString().trim());
            structured.setSeverityLevel(severity.toString().trim());
            structured.setRecommendedAction(action.toString().trim());
        }
        structured.setDisclaimer("This information is for educational purposes only and is not a substitute for professional medical advice.");
        response.setStructuredResponse(structured);
        return response;
    }

    /**
     * Create error response
     */
    private ChatResponse createErrorResponse() {
        ChatResponse response = new ChatResponse();
        response.setResponse("I apologize, but I'm unable to process your request at the moment. Please try again later.");
        response.setIsEmergency(false);
        return response;
    }
}
