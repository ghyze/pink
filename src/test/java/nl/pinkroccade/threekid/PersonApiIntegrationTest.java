package nl.pinkroccade.threekid;

import org.junit.jupiter.api.Test;
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
 * Integration test: starts the whole application on a random port and sends real HTTP requests. Each test starts
 * with an empty repository ({@link DirtiesContext}), and "today" is fixed at 1 January 2026.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PersonApiIntegrationTest {

    private static final int NO_RESPONSE = 444;

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

    @Test
    void familyWithAChildUnder18MatchesOnceTheChildsBirthDateIsKnown() {
        post("""
                { "id": 42, "name": "Ada", "birthDate": "1985-12-10",
                  "parent1": { "id": 1 }, "partner": { "id": 99 },
                  "children": [{ "id": 77 }, { "id": 78 }, { "id": 79 }] }
                """).expectStatus().isEqualTo(NO_RESPONSE).expectBody().isEmpty();

        // Both partners now list the same three children, but no child's birth date is known yet
        post("""
                { "id": 99, "name": "William", "partner": { "id": 42 },
                  "children": [{ "id": 77 }, { "id": 78 }, { "id": 79 }] }
                """).expectStatus().isEqualTo(NO_RESPONSE);

        post("""
                { "id": 79, "name": "Byron", "birthDate": "2015-05-12", "parent1": { "id": 42 }, "parent2": { "id": 99 } }
                """)
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
    void familyWithOnlyAdultChildrenDoesNotMatch() {
        addFamily();

        post("""
                { "id": 79, "birthDate": "2000-05-12", "parent1": { "id": 42 }, "parent2": { "id": 99 } }
                """).expectStatus().isEqualTo(NO_RESPONSE);
    }

    @Test
    void childsOwnRecordCanCompleteTheFamily() {
        post("""
                { "id": 42, "partner": { "id": 99 }, "children": [{ "id": 77 }, { "id": 78 }] }
                """).expectStatus().isEqualTo(NO_RESPONSE);
        post("""
                { "id": 77, "birthDate": "2015-01-01", "parent1": { "id": 42 }, "parent2": { "id": 99 } }
                """).expectStatus().isEqualTo(NO_RESPONSE);
        post("""
                { "id": 78, "parent1": { "id": 42 }, "parent2": { "id": 99 } }
                """).expectStatus().isEqualTo(NO_RESPONSE);

        // The third child is only known from its own record; autorepair adds it to 42 and 99
        post("""
                { "id": 79, "parent1": { "id": 42 }, "parent2": { "id": 99 } }
                """)
                .expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(2);
    }

    @Test
    void updateThatNoLongerMentionsAChildEndsTheMatch() {
        addFamilyWithYoungChild();

        post("""
                { "id": 99, "partner": { "id": 42 }, "children": [{ "id": 77 }, { "id": 78 }] }
                """).expectStatus().isEqualTo(NO_RESPONSE);
    }

    @Test
    void thirdParentIsRejectedAndChangesNothing() {
        addFamilyWithYoungChild();

        post("""
                { "id": 3, "children": [{ "id": 79 }] }
                """)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").isEqualTo("Person 79 already has two different parents. Cannot add parent 3.");

        // Sending a record that changes nothing shows that the family still matches
        post("""
                { "id": 79, "birthDate": "2015-05-12", "parent1": { "id": 42 }, "parent2": { "id": 99 } }
                """).expectStatus().isOk();
    }

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

    /** 42 and 99 are partners with three children: 77, 78 and 79. No birth dates of the children are known. */
    private void addFamily() {
        post("""
                { "id": 42, "partner": { "id": 99 }, "children": [{ "id": 77 }, { "id": 78 }, { "id": 79 }] }
                """).expectStatus().isEqualTo(NO_RESPONSE);
        post("""
                { "id": 99, "partner": { "id": 42 }, "children": [{ "id": 77 }, { "id": 78 }, { "id": 79 }] }
                """).expectStatus().isEqualTo(NO_RESPONSE);
    }

    /** The family of {@link #addFamily()}, where child 79 is 10 years old. */
    private void addFamilyWithYoungChild() {
        addFamily();
        post("""
                { "id": 79, "birthDate": "2015-05-12", "parent1": { "id": 42 }, "parent2": { "id": 99 } }
                """).expectStatus().isOk();
    }

    private RestTestClient.ResponseSpec post(String json) {
        return client.post().uri("/api/v1/people")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange();
    }
}
