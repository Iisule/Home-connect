package ng.proptech.service;

import java.util.List;
import java.util.Map;
import ng.proptech.config.AppProperties;
import ng.proptech.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Thin client for the local Python AI microservice's POST /generate endpoint. */
@Service
public class AiClientService {

    private static final Logger log = LoggerFactory.getLogger(AiClientService.class);

    /** Mirrors the AI service's JSON response shape (see ai-service/main.py). */
    public record AiGenerateResult(String imageUrl, String phash, int dimensions, List<Double> vector,
                                    String model, String source) {

        public float[] vectorAsFloatArray() {
            float[] out = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) out[i] = vector.get(i).floatValue();
            return out;
        }
    }

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AiClientService(RestTemplate aiRestTemplate, AppProperties props) {
        this.restTemplate = aiRestTemplate;
        this.baseUrl = props.ai().baseUrl();
    }

    @SuppressWarnings("unchecked")
    public AiGenerateResult generate(String imageUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("image_url", imageUrl), headers);

        try {
            Map<String, Object> body = restTemplate.postForObject(baseUrl + "/generate", request, Map.class);
            if (body == null) {
                throw new AiServiceException("AI service returned an empty response for " + imageUrl);
            }
            List<Double> vector = (List<Double>) body.get("vector");
            if (vector == null || vector.isEmpty()) {
                throw new AiServiceException("AI service response was missing a vector for " + imageUrl);
            }
            return new AiGenerateResult(
                    (String) body.get("image_url"),
                    (String) body.get("phash"),
                    ((Number) body.getOrDefault("dimensions", vector.size())).intValue(),
                    vector,
                    (String) body.get("model"),
                    (String) body.get("source"));
        } catch (RestClientException e) {
            log.error("AI microservice call failed for {}: {}", imageUrl, e.getMessage());
            throw new AiServiceException("Could not reach the AI photo-analysis service.", e);
        }
    }
}
