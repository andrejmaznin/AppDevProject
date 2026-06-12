package maznin.monitoring.error;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class ProblemCatalogControllerTest {

    private final WebTestClient webTestClient =
            WebTestClient.bindToController(new ProblemCatalogController()).build();

    @Test
    void catalogListsAllProblemTypesWithAllFields() {
        webTestClient.get()
                .uri("/api/v1/problems")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(ProblemType.values().length)
                .jsonPath("$[?(@.type == '/problems/patient-not-found')].status").isEqualTo(404)
                .jsonPath("$[?(@.type == '/problems/invalid-credentials')].status").isEqualTo(401)
                .jsonPath("$[?(@.type == '/problems/authentication-required')].status").isEqualTo(401)
                .jsonPath("$[*].title").exists()
                .jsonPath("$[*].description").exists()
                .jsonPath("$[*].remediation").exists();
    }

    @Test
    void everyTypeHasNonBlankRemediation() {
        for (ProblemType type : ProblemType.values()) {
            org.junit.jupiter.api.Assertions.assertFalse(type.getRemediation().isBlank(),
                    "Remediation must be filled for " + type);
            org.junit.jupiter.api.Assertions.assertFalse(type.getDescription().isBlank(),
                    "Description must be filled for " + type);
        }
    }
}
