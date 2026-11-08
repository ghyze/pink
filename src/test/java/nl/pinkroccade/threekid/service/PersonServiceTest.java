package nl.pinkroccade.threekid.service;

import nl.pinkroccade.threekid.domain.Person;
import nl.pinkroccade.threekid.dto.PersonDTO;
import nl.pinkroccade.threekid.dto.PersonReferenceDTO;
import nl.pinkroccade.threekid.repository.PersonRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario test: each step offers one more record to the same {@link PersonService} and builds on the state left
 * behind by the previous steps. See "PersonServiceTest" in README.md for the reasoning.
 * <p>
 * The steps share one test instance ({@link TestInstance.Lifecycle#PER_CLASS}) and run in {@link Order}.
 * Do not run a single step in isolation: it depends on the steps before it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersonServiceTest {

    private PersonRepository repository;
    private PersonService service;

    @BeforeAll
    void setUp() {
        repository = new PersonRepository();
        service = new PersonService(repository);
    }

    @Test
    @Order(1)
    void firstRecordIsStoredAndReferencedPeopleAreCreatedAsIncompleteRecords() {
        PersonDTO ada = new PersonDTO(
                42L, "Ada Lovelace", LocalDate.of(1815, 12, 10),
                ref(1L), ref(2L), ref(99L),
                List.of(ref(77L), ref(78L)));

        List<Person> matches = service.addPerson(ada);

        assertThat(matches).isEmpty();
        // TODO assert that 42 is stored with all its values
        // TODO assert that 1, 2, 99, 77 and 78 exist as incomplete records (ID only), linked to 42
    }

    @Test
    @Order(2)
    void recordForAnIncompletePersonCompletesThatRecord() {
        PersonDTO partner = new PersonDTO(
                99L, "William King", LocalDate.of(1805, 2, 21),
                null, null, ref(42L),
                List.of(ref(77L), ref(78L)));

        List<Person> matches = service.addPerson(partner);

        assertThat(matches).isEmpty();
        // TODO assert that 99 now has its name and birth date, and is still linked to 42, 77 and 78
    }

    @Test
    @Order(3)
    void recordForAnExistingPersonAmendsItsValues() {
        PersonDTO adaAmended = new PersonDTO(
                42L, "Ada King", LocalDate.of(1815, 12, 10),
                ref(1L), ref(2L), ref(99L),
                List.of(ref(77L), ref(78L), ref(79L)));

        List<Person> matches = service.addPerson(adaAmended);

        // TODO decide the expected matches once the three-kid pattern is implemented
        // TODO assert that 42 has its new name, and that 79 exists as an incomplete record
    }

    // TODO add further steps, e.g. a record that changes or removes an existing relation

    private static PersonReferenceDTO ref(long id) {
        return new PersonReferenceDTO(id);
    }
}
