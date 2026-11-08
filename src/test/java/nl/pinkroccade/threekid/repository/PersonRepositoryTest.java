package nl.pinkroccade.threekid.repository;

import nl.pinkroccade.threekid.domain.InvalidPersonException;
import nl.pinkroccade.threekid.domain.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonRepositoryTest {

    private PersonRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PersonRepository();
    }

    // --- find ---

    @Test
    void findPersonConvertsStoredPersonWithMapper() {
        repository.addOrUpdate(person(42, "Ada", null, null, null, List.of()));

        assertThat(repository.findPerson(42L, Person::getName)).contains("Ada");
    }

    @Test
    void findPersonReturnsEmptyForUnknownId() {
        assertThat(repository.findPerson(42L, Person::getName)).isEmpty();
    }

    // --- storing referenced people ---

    @Test
    void referencedUnknownPeopleAreStoredAsIncompletePeople() {
        repository.addOrUpdate(person(42, "Ada", 1L, 2L, 99L, List.of(77L)));

        assertThat(List.of(1L, 2L, 99L, 77L)).allSatisfy(id -> {
            Person stored = stored(id);
            assertThat(stored).as("person %d", id).isNotNull();
            assertThat(stored.getName()).isNull();
        });
    }

    @Test
    void relationsPointToTheStoredPeople() {
        repository.addOrUpdate(person(77, "Child", null, null, null, List.of()));
        repository.addOrUpdate(person(42, "Ada", null, null, null, List.of(77L)));

        Person child = stored(42L).getChildren().iterator().next();

        assertThat(child).isSameAs(stored(77L));
        assertThat(child.getName()).isEqualTo("Child");
    }

    // --- bidirectional integrity ---

    @Test
    void parentGetsChildWhenChildListsParent() {
        repository.addOrUpdate(person(1, "Parent", null, null, null, List.of()));
        repository.addOrUpdate(person(42, "Ada", 1L, null, null, List.of()));

        assertThat(stored(1L).getChildren()).extracting(Person::getId).containsExactly(42L);
    }

    @Test
    void childGetsParentWhenParentListsChild() {
        repository.addOrUpdate(person(77, "Child", null, null, null, List.of()));
        repository.addOrUpdate(person(42, "Ada", null, null, null, List.of(77L)));

        assertThat(stored(77L).getParents()).extracting(Person::getId).containsExactly(42L);
    }

    @Test
    void partnerGetsPartnerBack() {
        repository.addOrUpdate(person(99, "Partner", null, null, null, List.of()));
        repository.addOrUpdate(person(42, "Ada", null, null, 99L, List.of()));

        assertThat(stored(99L).getPartner().getId()).isEqualTo(42L);
    }

    @Test
    void previousPartnerIsReleasedWhenPartnerChooses() {
        repository.addOrUpdate(person(42, "Ada", null, null, 99L, List.of()));
        repository.addOrUpdate(person(43, "Other", null, null, 99L, List.of()));

        assertThat(stored(99L).getPartner().getId()).isEqualTo(43L);
        assertThat(stored(42L).getPartner()).isNull();
    }

    // --- updates ---

    @Test
    void updateReplacesValues() {
        repository.addOrUpdate(person(42, "Ada Lovelace", null, null, null, List.of()));
        repository.addOrUpdate(person(42, "Ada King", null, null, null, List.of()));

        assertThat(stored(42L).getName()).isEqualTo("Ada King");
    }

    @Test
    void updateRemovesRelationsThatAreNoLongerListed() {
        repository.addOrUpdate(person(42, "Ada", null, null, 99L, List.of(77L, 78L)));
        repository.addOrUpdate(person(42, "Ada", null, null, null, List.of(77L)));

        assertThat(stored(42L).getChildren()).extracting(Person::getId).containsExactly(77L);
        assertThat(stored(78L).getParents()).isEmpty();
        assertThat(stored(99L).getPartner()).isNull();
    }

    @Test
    void updateChangesTheStoredPersonInPlace() {
        repository.addOrUpdate(person(42, "Ada Lovelace", null, null, null, List.of()));
        Person before = stored(42L);

        repository.addOrUpdate(person(42, "Ada King", null, null, null, List.of()));

        assertThat(stored(42L)).isSameAs(before);
    }

    @Test
    void incompletePersonIsCompletedInPlace() {
        repository.addOrUpdate(person(42, "Ada", null, null, 99L, List.of()));
        Person incomplete = stored(99L);

        repository.addOrUpdate(person(99, "Partner", null, null, 42L, List.of()));

        assertThat(stored(99L)).isSameAs(incomplete);
        assertThat(stored(42L).getPartner().getName()).isEqualTo("Partner");
    }

    @Test
    void childRelationFromAnotherRecordIsBrokenWhenNotMentioned() {
        repository.addOrUpdate(person(77, "Child", 42L, null, null, List.of()));
        repository.addOrUpdate(person(42, "Ada", null, null, null, List.of()));

        assertThat(stored(77L).getParents()).isEmpty();
        assertThat(stored(42L).getChildren()).isEmpty();
    }

    @Test
    void parentRelationFromAnotherRecordIsBrokenWhenNotMentioned() {
        repository.addOrUpdate(person(1, "Parent", null, null, null, List.of(42L)));
        repository.addOrUpdate(person(42, "Ada", null, null, null, List.of()));

        assertThat(stored(1L).getChildren()).isEmpty();
        assertThat(stored(42L).getParents()).isEmpty();
    }

    @Test
    void partnerRelationFromAnotherRecordIsBrokenWhenNotMentioned() {
        repository.addOrUpdate(person(99, "Partner", null, null, 42L, List.of()));
        repository.addOrUpdate(person(42, "Ada", null, null, null, List.of()));

        assertThat(stored(99L).getPartner()).isNull();
        assertThat(stored(42L).getPartner()).isNull();
    }

    @Test
    void relationFromAnotherRecordIsKeptWhenMentioned() {
        repository.addOrUpdate(person(77, "Child", 42L, null, null, List.of()));
        repository.addOrUpdate(person(42, "Ada", null, null, null, List.of(77L)));

        assertThat(stored(77L).getParents()).containsExactly(stored(42L));
    }

    @Test
    void parentCanBeReplacedByTheChildsOwnRecord() {
        repository.addOrUpdate(person(77, "Child", 1L, 2L, null, List.of()));
        repository.addOrUpdate(person(77, "Child", 1L, 3L, null, List.of()));

        assertThat(stored(77L).getParents()).extracting(Person::getId).containsExactlyInAnyOrder(1L, 3L);
        assertThat(stored(2L).getChildren()).isEmpty();
    }

    @Test
    void parentListingAChildAgainIsNotAThirdParent() {
        repository.addOrUpdate(person(77, "Child", 1L, 2L, null, List.of()));

        repository.addOrUpdate(person(1, "Parent", null, null, null, List.of(77L)));

        assertThat(stored(77L).getParents()).extracting(Person::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void peopleReferringToAnUpdatedPersonSeeTheNewValues() {
        repository.addOrUpdate(person(77, "Child", 42L, null, null, List.of()));
        repository.addOrUpdate(person(42, "Ada", null, null, null, List.of(77L)));

        Person parent = stored(77L).getParents().iterator().next();

        assertThat(parent.getName()).isEqualTo("Ada");
    }

    // --- third parent ---

    @Test
    void thirdParentIsRejectedWithoutChangingAnything() {
        repository.addOrUpdate(person(77, "Child", 1L, 2L, null, List.of()));

        assertThatThrownBy(() -> repository.addOrUpdate(person(3, "Third", null, null, null, List.of(77L))))
                .isInstanceOf(InvalidPersonException.class);

        assertThat(stored(3L)).isNull();
        assertThat(stored(77L).getParents()).extracting(Person::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void rejectedUpdateKeepsTheOldRecord() {
        repository.addOrUpdate(person(77, "Child", 1L, 2L, null, List.of()));
        repository.addOrUpdate(person(3, "Third", null, null, 99L, List.of()));

        assertThatThrownBy(() -> repository.addOrUpdate(person(3, "Changed", null, null, null, List.of(77L))))
                .isInstanceOf(InvalidPersonException.class);

        assertThat(stored(3L).getName()).isEqualTo("Third");
        assertThat(stored(99L).getPartner().getId()).isEqualTo(3L);
    }

    // --- potential matches ---

    @Test
    void nobodyPotentiallyMatchesInAnEmptyRepository() {
        assertThat(potentialMatchIds()).isEmpty();
    }

    @Test
    void bothParentsPotentiallyMatchWhenTheyListTheSameThreeChildren() {
        addFamily();

        assertThat(potentialMatchIds()).containsExactlyInAnyOrder(42L, 99L);
    }

    @Test
    void personListingThreeChildrenThatAreNotThePartnersDoesNotMatch() {
        repository.addOrUpdate(person(42, "Ada", null, null, 99L, List.of(77L, 78L, 79L)));

        assertThat(potentialMatchIds()).isEmpty();
    }

    @Test
    void childsOwnRecordCanCompleteTheMatch() {
        repository.addOrUpdate(person(42, "Ada", null, null, 99L, List.of(77L, 78L, 79L)));
        repository.addOrUpdate(person(77, "Child", 42L, 99L, null, List.of()));
        repository.addOrUpdate(person(78, "Child", 42L, 99L, null, List.of()));
        assertThat(potentialMatchIds()).isEmpty();

        repository.addOrUpdate(person(79, "Child", 42L, 99L, null, List.of()));

        assertThat(potentialMatchIds()).containsExactlyInAnyOrder(42L, 99L);
    }

    @Test
    void matchIsRemovedWhenAChildDropsTheParent() {
        addFamily();

        repository.addOrUpdate(person(79, "Child", 42L, null, null, List.of()));

        assertThat(potentialMatchIds()).isEmpty();
    }

    @Test
    void matchIsRemovedWhenAFourthChildIsAdded() {
        addFamily();

        repository.addOrUpdate(person(80, "Child", 42L, 99L, null, List.of()));

        assertThat(potentialMatchIds()).isEmpty();
    }

    @Test
    void matchIsRemovedWhenThePartnerChoosesSomeoneElse() {
        addFamily();

        repository.addOrUpdate(person(99, "Partner", null, null, 43L, List.of(77L, 78L, 79L)));

        assertThat(potentialMatchIds()).isEmpty();
    }

    @Test
    void matchIsRemovedWhenSomeoneElseTakesThePartner() {
        addFamily();

        repository.addOrUpdate(person(43, "Other", null, null, 99L, List.of()));

        assertThat(potentialMatchIds()).isEmpty();
    }

    @Test
    void rejectedRecordDoesNotChangeThePotentialMatches() {
        addFamily();

        assertThatThrownBy(() -> repository.addOrUpdate(person(3, "Third", null, null, null, List.of(77L))))
                .isInstanceOf(InvalidPersonException.class);

        assertThat(potentialMatchIds()).containsExactlyInAnyOrder(42L, 99L);
    }

    @Test
    void findPotentialMatchesAppliesFilterAndMapper() {
        addFamily();

        List<String> names = repository.findPotentialMatches(p -> p.getId() == 42L, Person::getName);

        assertThat(names).containsExactly("Ada");
    }

    /** Returns the stored object itself; safe here, because the tests run on a single thread. */
    private Person stored(long id) {
        return repository.findPerson(id, Function.identity()).orElse(null);
    }

    private List<Long> potentialMatchIds() {
        return repository.findPotentialMatches(p -> true, Person::getId);
    }

    /** 42 and 99 are partners with three children: 77, 78 and 79. */
    private void addFamily() {
        repository.addOrUpdate(person(42, "Ada", null, null, 99L, List.of(77L, 78L, 79L)));
        repository.addOrUpdate(person(99, "Partner", null, null, 42L, List.of(77L, 78L, 79L)));
    }

    /** Builds a person as {@code PersonDTO.toPerson()} does: relations are incomplete people with only an ID. */
    private static Person person(long id, String name, Long parent1, Long parent2, Long partner, List<Long> children) {
        return new Person(id, name, LocalDate.of(1990, 1, 1),
                parent1 == null ? null : new Person(parent1),
                parent2 == null ? null : new Person(parent2),
                partner == null ? null : new Person(partner),
                children.stream().map(Person::new).toList());
    }
}
