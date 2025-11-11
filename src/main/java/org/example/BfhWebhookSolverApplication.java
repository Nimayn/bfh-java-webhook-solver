package org.example;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class BfhWebhookSolverApplication implements CommandLineRunner {

    private final RestTemplate restTemplate;

    public BfhWebhookSolverApplication(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(BfhWebhookSolverApplication.class, args);
    }

    @Override
    public void run(String... args) {
        try {
            System.out.println("Starting webhook generation flow...");

            // 1) Prepare the generateWebhook request body
            Map<String, String> req = new HashMap<>();
            req.put("name", "John Doe");
            req.put("regNo", "REG12348"); // change to your reg no (last two digits decide question)
            req.put("email", "john@example.com");

            String generateUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(req, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(generateUrl, entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Failed to generate webhook: status=" + response.getStatusCode());
            }

            Map body = response.getBody();
            System.out.println("generateWebhook response: " + body);

            // Expect keys "webhook" and "accessToken" (adjust if API returns different fields)
            String webhook = (String) body.get("webhook");
            String accessToken = (String) body.get("accessToken");

            if (webhook == null || accessToken == null) {
                throw new IllegalStateException("Missing webhook or accessToken in response: " + body);
            }

            // 2) Compose the final SQL query (for the provided Question file)
            String finalQuery = """
                    SELECT
                      e.emp_id,
                      e.first_name,
                      e.last_name,
                      d.department_name,
                      (
                        SELECT COUNT(*)
                        FROM employee e2
                        WHERE e2.department = e.department
                          AND e2.dob > e.dob
                      ) AS younger_employees_count
                    FROM employee e
                    JOIN department d ON e.department = d.department_id
                    ORDER BY e.emp_id DESC;
                    """;

            // 3) Send the final query to the testWebhook endpoint
            String testWebhookUrl = "https://bfhldevapigw.healthrx.co.in/hiring/testWebhook/JAVA";

            HttpHeaders submitHeaders = new HttpHeaders();
            submitHeaders.setContentType(MediaType.APPLICATION_JSON);

            // The assignment says: Headers: Authorization: <accessToken>
            submitHeaders.set("Authorization", accessToken);

            Map<String, String> submitBody = new HashMap<>();
            submitBody.put("finalQuery", finalQuery);

            HttpEntity<Map<String, String>> submitEntity = new HttpEntity<>(submitBody, submitHeaders);

            ResponseEntity<String> submitResponse = restTemplate.postForEntity(testWebhookUrl, submitEntity, String.class);

            System.out.println("Submission response status: " + submitResponse.getStatusCode());
            System.out.println("Submission response body: " + submitResponse.getBody());

            System.out.println("Flow completed.");

        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("ERROR during flow: " + ex.getMessage());
            System.exit(1); // fail loudly — assignment expects the run to finish and return result
        }
    }
}
