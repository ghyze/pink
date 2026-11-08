package nl.pinkroccade.threekid;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Integration test: starts the whole application on a random port and sends real HTTP requests. "Today" is fixed at
 * 1 January 2026.
 * <p>
 * The {@link Order}ed tests are one scenario: each step sends one more record and builds on the state left behind by
 * the previous steps, in the application context that all tests in this class share. Do not run a single step in
 * isolation. The tests without an order use their own IDs or change nothing, so they do not disturb the scenario.
 * After the class, the context is thrown away ({@link DirtiesContext}), so no other test class sees its data.
 * See "PersonApiIntegrationTest" in README.md for the reasoning.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersonApiIntegrationTest {

    private static final int NO_RESPONSE = 444;

    private static final String BYRON_AS_CHILD = """
            { "id": 79, "name": "Byron", "birthDate": "2015-05-12", "parent1": { "id": 42 }, "parent2": { "id": 99 } }
            """;

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        }
    }

    @Autowired
    private RestTestClient client;

    // --- scenario: 42 and 99 are partners with three children: 77, 78 and 79 ---

    @Test
    @Order(1)
    void firstParentWithThreeChildrenDoesNotMatchYet() {
        post("""
                { "id": 42, "name": "Ada", "birthDate": "1985-12-10",
                  "parent1": { "id": 1 }, "partner": { "id": 99 },
                  "children": [{ "id": 77 }, { "id": 78 }, { "id": 79 }] }
                """).expectStatus().isEqualTo(NO_RESPONSE).expectBody().isEmpty();
    }

    @Test
    @Order(2)
    void familyWithoutKnownBirthDatesDoesNotMatch() {
        // Both partners now list the same three children, but no child's birth date is known yet
        post("""
                { "id": 99, "name": "William", "partner": { "id": 42 },
                  "children": [{ "id": 77 }, { "id": 78 }, { "id": 79 }] }
                """).expectStatus().isEqualTo(NO_RESPONSE);
    }

    @Test
    @Order(3)
    void familyWithOnlyAdultChildrenDoesNotMatch() {
        post("""
                { "id": 79, "name": "Byron", "birthDate": "2000-05-12", "parent1": { "id": 42 }, "parent2": { "id": 99 } }
                """).expectStatus().isEqualTo(NO_RESPONSE);
    }

    @Test
    @Order(4)
    void familyWithAChildUnder18Matches() {
        post(BYRON_AS_CHILD)
                .expectStatus().isOk()
                .expectBody().json("""
                        [
                          { "id": 42, "name": "Ada", "birthDate": "1985-12-10",
                            "parent1": { "id": 1 }, "parent2": null, "partner": { "id": 99 },
                            "children": [{ "id": 77 }, { "id": 78 }, { "id": 79 }] },
                          { "id": 99, "name": "William", "birthDate": null,
                            "parent1": null, "parent2": null, "partner": { "id": 42 },
                            "children": [{ "id": 77 }, { "id": 78 }, { "id": 79 }] }
                        ]
                        """);
    }

    @Test
    @Order(5)
    void thirdParentIsRejectedAndChangesNothing() {
        post("""
                { "id": 3, "children": [{ "id": 79 }] }
                """)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").isEqualTo("Person 79 already has two different parents. Cannot add parent 3.");

        // Sending a record that changes nothing shows that the family still matches
        post(BYRON_AS_CHILD).expectStatus().isOk();
    }

    @Test
    @Order(6)
    void updateThatNoLongerMentionsAChildEndsTheMatch() {
        post("""
                { "id": 99, "name": "William", "partner": { "id": 42 }, "children": [{ "id": 77 }, { "id": 78 }] }
                """).expectStatus().isEqualTo(NO_RESPONSE);
    }

    // --- independent of the scenario ---

    /** Uses its own IDs (1xx), so it does not touch the scenario's family. */
    @Test
    void childsOwnRecordCanCompleteTheFamily() {
        post("""
                { "id": 142, "partner": { "id": 199 }, "children": [{ "id": 177 }, { "id": 178 }] }
                """).expectStatus().isEqualTo(NO_RESPONSE);
        post("""
                { "id": 177, "birthDate": "2015-01-01", "parent1": { "id": 142 }, "parent2": { "id": 199 } }
                """).expectStatus().isEqualTo(NO_RESPONSE);
        post("""
                { "id": 178, "parent1": { "id": 142 }, "parent2": { "id": 199 } }
                """).expectStatus().isEqualTo(NO_RESPONSE);

        // The third child is only known from its own record; autorepair adds it to 142 and 199
        post("""
                { "id": 179, "parent1": { "id": 142 }, "parent2": { "id": 199 } }
                """)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.id == 142)]").exists()
                .jsonPath("$[?(@.id == 199)]").exists();
    }

    /** A rejected record stores nothing, so these can run at any moment. */
    @ParameterizedTest
    @ValueSource(strings = {
            "{ \"name\": \"No id\" }",
            "{ \"id\": 42, \"parent1\": {} }",
            "{ \"id\": 42, \"children\": [{ \"id\": 77 }, null] }",
            "{ \"id\": 42, \"partner\": { \"id\": 42 } }",
            "{ \"id\": 42, \"birthDate\": \"not a date\" }",
            "{ \"id\": 42, "
    })
    void invalidRecordIsRejected(String json) {
        post(json).expectStatus().isBadRequest();
    }

    private RestTestClient.ResponseSpec post(String json) {
        return client.post().uri("/api/v1/people")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange();
    }
}
