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

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario test: each step offers one more record to the same {@link PersonService} and builds on the state left
 * behind by the previous steps. See "PersonServiceTest" in README.md for the reasoning.
 * <p>
 * The steps share one test instance ({@link TestInstance.Lifecycle#PER_CLASS}) and run in {@link Order}.
 * Do not run a single step in isolation: it depends on the steps before it.
 * <p>
 * The service uses a fixed clock on 1 January 1840, so Byron (born 1836) is under 18.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersonServiceTest {

    private PersonRepository repository;
    private PersonService service;

    @BeforeAll
    void setUp() {
        repository = new PersonRepository();
        service = new PersonService(repository, clockOn(1840, 1, 1));
    }

    @Test
    @Order(1)
    void firstRecordIsStoredAndReferencedPeopleAreCreatedAsIncompleteRecords() {
        PersonDTO ada = new PersonDTO(
                42L, "Ada Lovelace", LocalDate.of(1815, 12, 10),
                ref(1L), ref(2L), ref(99L),
                List.of(ref(77L), ref(78L)));

        List<PersonDTO> matches = service.addPerson(ada);

        assertThat(matches).isEmpty();
        assertThat(PersonDTO.from(stored(42L))).isEqualTo(ada);
        assertThat(List.of(1L, 2L, 99L, 77L, 78L)).allSatisfy(id -> {
            Person relative = stored(id);
            assertThat(relative.getName()).as("name of %d", id).isNull();
            assertThat(relative.getBirthDate()).as("birth date of %d", id).isNull();
        });
        assertThat(stored(1L).getChildren()).extracting(Person::getId).containsExactly(42L);
        assertThat(stored(99L).getPartner().getId()).isEqualTo(42L);
        assertThat(stored(77L).getParents()).extracting(Person::getId).containsExactly(42L);
    }

    @Test
    @Order(2)
    void recordForAnIncompletePersonCompletesThatRecord() {
        PersonDTO partner = new PersonDTO(
                99L, "William King", LocalDate.of(1805, 2, 21),
                null, null, ref(42L),
                List.of(ref(77L), ref(78L)));

        List<PersonDTO> matches = service.addPerson(partner);

        assertThat(matches).isEmpty();
        Person stored = stored(99L);
        assertThat(stored.getName()).isEqualTo("William King");
        assertThat(stored.getBirthDate()).isEqualTo(LocalDate.of(1805, 2, 21));
        assertThat(stored.getPartner().getId()).isEqualTo(42L);
        assertThat(stored.getChildren()).extracting(Person::getId).containsExactlyInAnyOrder(77L, 78L);
        assertThat(stored(77L).getParents()).extracting(Person::getId).containsExactlyInAnyOrder(42L, 99L);
    }

    @Test
    @Order(3)
    void recordForAnExistingPersonAmendsItsValues() {
        PersonDTO adaAmended = new PersonDTO(
                42L, "Ada King", LocalDate.of(1815, 12, 10),
                ref(1L), ref(2L), ref(99L),
                List.of(ref(77L), ref(78L), ref(79L)));

        List<PersonDTO> matches = service.addPerson(adaAmended);

        // 42 has three children now, but 79 does not have partner 99 as parent
        assertThat(matches).isEmpty();
        assertThat(stored(42L).getName()).isEqualTo("Ada King");
        assertThat(stored(79L).getName()).isNull();
        assertThat(stored(79L).getParents()).extracting(Person::getId).containsExactly(42L);
    }

    @Test
    @Order(4)
    void childsOwnRecordCompletesTheFamily() {
        PersonDTO child = new PersonDTO(
                79L, "Byron King", LocalDate.of(1836, 5, 12),
                ref(42L), ref(99L), null,
                null);

        List<PersonDTO> matches = service.addPerson(child);

        assertThat(matches).extracting(PersonDTO::id).containsExactlyInAnyOrder(42L, 99L);

        // The same family twenty years later: Byron is an adult, so nobody matches
        PersonService later = new PersonService(repository, clockOn(1860, 1, 1));
        assertThat(later.addPerson(child)).isEmpty();
    }

    @Test
    @Order(5)
    void recordThatNoLongerMentionsAChildBreaksTheFamily() {
        PersonDTO partner = new PersonDTO(
                99L, "William King", LocalDate.of(1805, 2, 21),
                null, null, ref(42L),
                List.of(ref(77L), ref(78L)));

        List<PersonDTO> matches = service.addPerson(partner);

        assertThat(matches).isEmpty();
        assertThat(stored(79L).getParents()).extracting(Person::getId).containsExactly(42L);
    }

    private static Clock clockOn(int year, int month, int day) {
        return Clock.fixed(LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }

    /** Returns the stored object itself; safe here, because the tests run on a single thread. */
    private Person stored(long id) {
        return repository.findPerson(id, Function.identity()).orElse(null);
    }

    private static PersonReferenceDTO ref(long id) {
        return new PersonReferenceDTO(id);
    }
}
