package nl.pinkroccade.threekid.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonTest {

    private static final LocalDate BIRTHDATE = LocalDate.of(1815, 12, 10);

    @Test
    void constructorStoresAllValues() {
        Person parent1 = new Person(1);
        Person parent2 = new Person(2);
        Person partner = new Person(99);
        Person child = new Person(77);

        Person person = new Person(42, "Ada Lovelace", BIRTHDATE, parent1, parent2, partner, List.of(child));

        assertThat(person.getId()).isEqualTo(42);
        assertThat(person.getName()).isEqualTo("Ada Lovelace");
        assertThat(person.getBirthDate()).isEqualTo(BIRTHDATE);
        assertThat(person.getParents()).containsExactlyInAnyOrder(parent1, parent2);
        assertThat(person.getPartner()).isSameAs(partner);
        assertThat(person.getChildren()).containsExactly(child);
    }

    @Test
    void constructorIgnoresMissingParents() {
        Person parent1 = new Person(1);

        Person person = new Person(42, "Ada Lovelace", BIRTHDATE, parent1, null, null, List.of());

        assertThat(person.getParents()).containsExactly(parent1);
    }

    @Test
    void constructorWithoutParentsGivesNoParents() {
        Person person = new Person(42, "Ada Lovelace", BIRTHDATE, null, null, null, List.of());

        assertThat(person.getParents()).isEmpty();
    }

    @Test
    void constructorAcceptsMissingChildren() {
        Person person = new Person(42, "Ada Lovelace", BIRTHDATE, null, null, null, null);

        assertThat(person.getChildren()).isEmpty();
    }

    @Test
    void idOnlyConstructorCreatesIncompletePerson() {
        Person person = new Person(42);

        assertThat(person.getId()).isEqualTo(42);
        assertThat(person.getName()).isNull();
        assertThat(person.getBirthDate()).isNull();
        assertThat(person.getParents()).isEmpty();
        assertThat(person.getPartner()).isNull();
        assertThat(person.getChildren()).isEmpty();
    }

    @Test
    void addParentAddsUpToTwoParents() {
        Person person = new Person(42);
        Person parent1 = new Person(1);
        Person parent2 = new Person(2);

        person.addParent(parent1);
        person.addParent(parent2);

        assertThat(person.getParents()).containsExactlyInAnyOrder(parent1, parent2);
    }

    @Test
    void addParentRejectsAThirdDifferentParent() {
        Person person = new Person(42);
        person.addParent(new Person(1));
        person.addParent(new Person(2));

        assertThatThrownBy(() -> person.addParent(new Person(3)))
                .isInstanceOf(InvalidPersonException.class);
        assertThat(person.getParents()).hasSize(2);
    }

    @Test
    void addParentWithKnownIdDoesNotAddAThirdParent() {
        Person person = new Person(42);
        person.addParent(new Person(1));
        person.addParent(new Person(2));

        person.addParent(new Person(1));

        assertThat(person.getParents()).extracting(Person::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void addChildWithKnownIdDoesNotAddItTwice() {
        Person person = new Person(42);

        person.addChild(new Person(77));
        person.addChild(new Person(77));

        assertThat(person.getChildren()).extracting(Person::getId).containsExactly(77L);
    }

    @Test
    void setPartnerReplacesPartner() {
        Person person = new Person(42);
        Person newPartner = new Person(100);
        person.setPartner(new Person(99));

        person.setPartner(newPartner);

        assertThat(person.getPartner()).isSameAs(newPartner);
    }

    @Test
    void settersCompleteAnIncompletePerson() {
        Person person = new Person(42);

        person.setName("Ada Lovelace");
        person.setBirthDate(BIRTHDATE);

        assertThat(person.getName()).isEqualTo("Ada Lovelace");
        assertThat(person.getBirthDate()).isEqualTo(BIRTHDATE);
    }

    @Test
    void peopleWithTheSameIdAreEqual() {
        Person complete = new Person(42, "Ada Lovelace", BIRTHDATE, null, null, null, List.of());
        Person incomplete = new Person(42);

        assertThat(complete).isEqualTo(incomplete).hasSameHashCodeAs(incomplete);
    }

    @Test
    void peopleWithDifferentIdsAreNotEqual() {
        assertThat(new Person(42)).isNotEqualTo(new Person(43));
    }

    // --- potentiallyMatches ---

    @Test
    void personWithPartnerAndThreeChildrenOfThatPartnerPotentiallyMatches() {
        Person person = family(3, 3);

        assertThat(person.potentiallyMatches()).isTrue();
    }

    @Test
    void personWithoutPartnerDoesNotMatch() {
        Person person = family(3, 3);
        person.setPartner(null);

        assertThat(person.potentiallyMatches()).isFalse();
    }

    @Test
    void personWithTwoChildrenDoesNotMatch() {
        assertThat(family(2, 2).potentiallyMatches()).isFalse();
    }

    @Test
    void personWithFourChildrenDoesNotMatch() {
        assertThat(family(4, 4).potentiallyMatches()).isFalse();
    }

    @Test
    void personWithAChildOfAnotherParentDoesNotMatch() {
        assertThat(family(3, 2).potentiallyMatches()).isFalse();
    }

    // --- hasChildUnder18 ---

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    @Test
    void childOneDayBeforeEighteenthBirthdayIsUnder18() {
        assertThat(parentOfChildBornOn(TODAY.minusYears(18).plusDays(1)).hasChildUnder18(TODAY)).isTrue();
    }

    @Test
    void childOnEighteenthBirthdayIsNotUnder18() {
        assertThat(parentOfChildBornOn(TODAY.minusYears(18)).hasChildUnder18(TODAY)).isFalse();
    }

    @Test
    void childWithoutBirthDateDoesNotCountAsUnder18() {
        assertThat(parentOfChildBornOn(null).hasChildUnder18(TODAY)).isFalse();
    }

    @Test
    void oneChildUnder18IsEnough() {
        Person parent = parentOfChildBornOn(TODAY.minusYears(30));
        parent.addChild(new Person(78, "Young", TODAY.minusYears(5), null, null, null, List.of()));

        assertThat(parent.hasChildUnder18(TODAY)).isTrue();
    }

    @Test
    void personWithoutChildrenHasNoChildUnder18() {
        assertThat(new Person(42).hasChildUnder18(TODAY)).isFalse();
    }

    private static Person parentOfChildBornOn(LocalDate birthDate) {
        Person parent = new Person(42);
        parent.addChild(new Person(77, "Child", birthDate, null, null, null, List.of()));
        return parent;
    }

    /**
     * Builds person 42 with partner 99 and the given number of children; the first {@code withPartner} children
     * also have 99 as parent.
     */
    private static Person family(int children, int withPartner) {
        Person person = new Person(42);
        Person partner = new Person(99);
        person.setPartner(partner);
        partner.setPartner(person);
        for (int i = 0; i < children; i++) {
            Person child = new Person(70 + i);
            child.addParent(person);
            person.addChild(child);
            if (i < withPartner) {
                child.addParent(partner);
                partner.addChild(child);
            }
        }
        return person;
    }

    @Test
    void returnedCollectionsCannotChangeThePerson() {
        Person person = new Person(42);
        person.addParent(new Person(1));
        person.addChild(new Person(77));

        assertThatThrownBy(() -> person.getParents().add(new Person(2)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> person.getChildren().add(new Person(78)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
