package com.healthcare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.dto.LocationChatResponse;
import com.healthcare.dto.LocationChatResponse.DoctorDTO;
import com.healthcare.dto.LocationChatResponse.HospitalDTO;
import com.healthcare.model.Doctor;
import com.healthcare.repository.DoctorRepository;
import com.healthcare.util.HaversineUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for location-aware medical chat: - Detects required specialization
 * from message keywords - Queries DB for nearby available doctors (Haversine, ≤
 * 10 km) - Fetches nearby hospitals from OpenStreetMap Overpass API (≤ 5 km) -
 * Gets AI suggestion from Ollama (or OpenAI fallback)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LocationChatService {

    private final DoctorRepository doctorRepository;
    private final OpenAIService openAIService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final double DOCTOR_RADIUS_KM = 10.0;
    private static final double HOSPITAL_RADIUS_KM = 5.0;
    private static final int MAX_DOCTORS = 5;
    private static final int MAX_HOSPITALS = 5;

    // Keyword → specialization mapping
    private static final Map<String, String> KEYWORD_SPECIALIZATION = new LinkedHashMap<>();

    static {
        KEYWORD_SPECIALIZATION.put("chest pain", "Cardiologist");
        KEYWORD_SPECIALIZATION.put("heart", "Cardiologist");
        KEYWORD_SPECIALIZATION.put("palpitation", "Cardiologist");
        KEYWORD_SPECIALIZATION.put("skin rash", "Dermatologist");
        KEYWORD_SPECIALIZATION.put("rash", "Dermatologist");
        KEYWORD_SPECIALIZATION.put("acne", "Dermatologist");
        KEYWORD_SPECIALIZATION.put("eye pain", "Ophthalmologist");
        KEYWORD_SPECIALIZATION.put("blurry vision", "Ophthalmologist");
        KEYWORD_SPECIALIZATION.put("red eye", "Ophthalmologist");
        KEYWORD_SPECIALIZATION.put("tooth", "Dentist");
        KEYWORD_SPECIALIZATION.put("dental", "Dentist");
        KEYWORD_SPECIALIZATION.put("bone", "Orthopedist");
        KEYWORD_SPECIALIZATION.put("joint pain", "Orthopedist");
        KEYWORD_SPECIALIZATION.put("fracture", "Orthopedist");
        KEYWORD_SPECIALIZATION.put("child", "Pediatrician");
        KEYWORD_SPECIALIZATION.put("baby", "Pediatrician");
        KEYWORD_SPECIALIZATION.put("mental", "Psychiatrist");
        KEYWORD_SPECIALIZATION.put("anxiety", "Psychiatrist");
        KEYWORD_SPECIALIZATION.put("depression", "Psychiatrist");
        KEYWORD_SPECIALIZATION.put("stomach", "Gastroenterologist");
        KEYWORD_SPECIALIZATION.put("diarrhea", "Gastroenterologist");
        KEYWORD_SPECIALIZATION.put("vomit", "Gastroenterologist");
        // Default
        KEYWORD_SPECIALIZATION.put("fever", "General Physician");
        KEYWORD_SPECIALIZATION.put("body pain", "General Physician");
        KEYWORD_SPECIALIZATION.put("headache", "General Physician");
        KEYWORD_SPECIALIZATION.put("cold", "General Physician");
        KEYWORD_SPECIALIZATION.put("cough", "General Physician");
        KEYWORD_SPECIALIZATION.put("fatigue", "General Physician");
    }

    /**
     * Main entry: process location-aware chat request — parallel execution
     */
    public LocationChatResponse process(String message, Double lat, Double lon) {
        // 1. Detect specialization
        String specialization = detectSpecialization(message);
        log.info("Detected specialization: {} for message: {}", specialization, message);

        // 2. Run AI + DB + Overpass in parallel
        CompletableFuture<String[]> aiFuture = CompletableFuture.supplyAsync(() -> getAiSuggestion(message, specialization));

        CompletableFuture<List<DoctorDTO>> doctorFuture = CompletableFuture.supplyAsync(()
                -> (lat != null && lon != null) ? findNearbyDoctors(lat, lon, specialization) : Collections.emptyList());

        CompletableFuture<List<HospitalDTO>> hospitalFuture = CompletableFuture.supplyAsync(()
                -> (lat != null && lon != null) ? fetchNearbyHospitals(lat, lon) : Collections.emptyList());

        // Wait for all
        CompletableFuture.allOf(aiFuture, doctorFuture, hospitalFuture).join();

        String[] aiSuggestion = aiFuture.join();
        return LocationChatResponse.builder()
                .aiSuggestionEnglish(aiSuggestion[0])
                .aiSuggestionTelugu(aiSuggestion[1])
                .recommendedDoctors(doctorFuture.join())
                .nearbyHospitals(hospitalFuture.join())
                .build();
    }

    // ──────────────────────────────────────────────────────────────
    // Specialization detection
    // ──────────────────────────────────────────────────────────────
    public String detectSpecialization(String message) {
        String lower = message.toLowerCase();
        for (Map.Entry<String, String> entry : KEYWORD_SPECIALIZATION.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "General Physician";
    }

    // ──────────────────────────────────────────────────────────────
    // AI suggestion via OpenAIService (Ollama / OpenAI) — returns [english, telugu]
    // ──────────────────────────────────────────────────────────────
    private String[] getAiSuggestion(String message, String specialization) {
        String fallbackEn = "Based on your symptoms, I recommend consulting a " + specialization
                + ". Please seek professional medical advice for an accurate diagnosis.";
        String fallbackTe = "మీ లక్షణాల ఆధారంగా, " + specialization + "ని సంప్రదించమని సిఫారసు చేస్తున్నాను. సరైన నిర్ధారణ కోసం వైద్య సహాయం తీసుకోండి.";
        try {
            String systemPrompt
                    = "You are a medical assistant. User symptoms: \"" + message + "\"\n"
                    + "Recommended specialist: " + specialization + "\n\n"
                    + "Reply ONLY as JSON (no code fences, no extra text):\n"
                    + "{\"aiSuggestionEnglish\":\"brief English advice max 60 words\","
                    + "\"aiSuggestionTelugu\":\"same advice in Telugu\"}";

            List<Map<String, String>> msgs = new ArrayList<>();
            msgs.add(Map.of("role", "user", "content", systemPrompt));
            String raw = openAIService.callOllama(msgs);

            // Extract JSON from response (model may wrap it in markdown code block)
            String jsonStr = extractJson(raw);
            JsonNode node = objectMapper.readTree(jsonStr);
            String en = node.has("aiSuggestionEnglish") ? node.get("aiSuggestionEnglish").asText() : fallbackEn;
            String te = node.has("aiSuggestionTelugu") ? node.get("aiSuggestionTelugu").asText() : fallbackTe;
            return new String[]{en, te};
        } catch (Exception e) {
            log.warn("AI bilingual suggestion parse failed, using fallback. Reason: {}", e.getMessage());
            return new String[]{fallbackEn, fallbackTe};
        }
    }

    /**
     * Strip markdown code fences and find the first {...} JSON block
     */
    private String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        // Remove ```json ... ``` fences
        String cleaned = raw.replaceAll("```[a-zA-Z]*\\n?", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    // ──────────────────────────────────────────────────────────────
    // Nearby doctors from DB
    // ──────────────────────────────────────────────────────────────
    private List<DoctorDTO> findNearbyDoctors(double lat, double lon, String specialization) {
        // Try matched specialization first, fall back to all available
        List<Doctor> candidates = doctorRepository.findBySpecializationAndAvailableTrue(specialization);
        if (candidates.isEmpty()) {
            candidates = doctorRepository.findByAvailableTrue();
        }

        return candidates.stream()
                .map(d -> {
                    double dist = HaversineUtil.distanceKm(lat, lon, d.getLatitude(), d.getLongitude());
                    return new AbstractMap.SimpleEntry<>(d, dist);
                })
                .filter(e -> e.getValue() <= DOCTOR_RADIUS_KM)
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .limit(MAX_DOCTORS)
                .map(e -> DoctorDTO.builder()
                .id(e.getKey().getId())
                .name(e.getKey().getName())
                .specialization(e.getKey().getSpecialization())
                .distance(HaversineUtil.format(e.getValue()))
                .build())
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────────
    // Nearby hospitals from OpenStreetMap Overpass API
    // ──────────────────────────────────────────────────────────────
    private List<HospitalDTO> fetchNearbyHospitals(double lat, double lon) {
        try {
            int radiusMeters = (int) (HOSPITAL_RADIUS_KM * 1000);
            String query = String.format(
                    "[out:json][timeout:10];(node[\"amenity\"=\"hospital\"](around:%d,%f,%f);"
                    + "way[\"amenity\"=\"hospital\"](around:%d,%f,%f););out center;",
                    radiusMeters, lat, lon, radiusMeters, lat, lon
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<String> request = new HttpEntity<>("data=" + query, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://overpass-api.de/api/interpreter", request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseHospitals(response.getBody(), lat, lon);
            }
        } catch (Exception e) {
            log.warn("Overpass API call failed: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<HospitalDTO> parseHospitals(String json, double userLat, double userLon) {
        List<HospitalDTO> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode elements = root.get("elements");
            if (elements == null || !elements.isArray()) {
                return result;
            }

            for (JsonNode el : elements) {
                String name = null;
                JsonNode tags = el.get("tags");
                if (tags != null && tags.has("name")) {
                    name = tags.get("name").asText();
                }
                if (name == null || name.isBlank()) {
                    name = "Unnamed Hospital";
                }

                double hLat, hLon;
                if (el.has("center")) {
                    hLat = el.get("center").get("lat").asDouble();
                    hLon = el.get("center").get("lon").asDouble();
                } else {
                    hLat = el.has("lat") ? el.get("lat").asDouble() : userLat;
                    hLon = el.has("lon") ? el.get("lon").asDouble() : userLon;
                }

                result.add(HospitalDTO.builder()
                        .name(name)
                        .latitude(hLat)
                        .longitude(hLon)
                        .build());

                if (result.size() >= MAX_HOSPITALS) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Hospital JSON parse error: {}", e.getMessage());
        }
        return result;
    }
}
