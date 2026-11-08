package nl.pinkroccade.threekid.dto;

import nl.pinkroccade.threekid.domain.InvalidPersonException;
import nl.pinkroccade.threekid.domain.Person;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonDTOTest {

    private static final LocalDate BIRTHDATE = LocalDate.of(1815, 12, 10);

    @Test
    void toPersonConvertsAllValuesAndReferences() {
        PersonDTO dto = new PersonDTO(42L, "Ada Lovelace", BIRTHDATE,
                ref(1L), ref(2L), ref(99L), List.of(ref(77L), ref(78L)));

        Person person = dto.toPerson();

        assertThat(person.getId()).isEqualTo(42);
        assertThat(person.getName()).isEqualTo("Ada Lovelace");
        assertThat(person.getBirthDate()).isEqualTo(BIRTHDATE);
        assertThat(person.getParents()).extracting(Person::getId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(person.getPartner().getId()).isEqualTo(99);
        assertThat(person.getChildren()).extracting(Person::getId).containsExactlyInAnyOrder(77L, 78L);
    }

    @Test
    void toPersonCreatesReferencedPeopleAsIncompletePeople() {
        PersonDTO dto = new PersonDTO(42L, "Ada Lovelace", BIRTHDATE,
                null, null, ref(99L), List.of());

        Person partner = dto.toPerson().getPartner();

        assertThat(partner.getName()).isNull();
        assertThat(partner.getBirthDate()).isNull();
    }

    /** JSON in which parents, partner and children are left out. */
    @Test
    void toPersonAcceptsMissingRelations() {
        PersonDTO dto = new PersonDTO(42L, "Ada Lovelace", BIRTHDATE, null, null, null, null);

        Person person = dto.toPerson();

        assertThat(person.getParents()).isEmpty();
        assertThat(person.getPartner()).isNull();
        assertThat(person.getChildren()).isEmpty();
    }

    /** JSON such as {@code "children": [{ "id": 77 }, null]}. */
    @Test
    void personWithNullChildIsRejected() {
        List<PersonReferenceDTO> children = Arrays.asList(ref(77L), null);

        assertThatThrownBy(() -> new PersonDTO(42L, "Ada Lovelace", BIRTHDATE, null, null, null, children))
                .isInstanceOf(InvalidPersonException.class);
    }

    @Test
    void personWhoIsTheirOwnParentIsRejected() {
        assertThatThrownBy(() -> new PersonDTO(42L, "Ada Lovelace", BIRTHDATE, ref(1L), ref(42L), null, null))
                .isInstanceOf(InvalidPersonException.class);
    }

    @Test
    void personWhoIsTheirOwnPartnerIsRejected() {
        assertThatThrownBy(() -> new PersonDTO(42L, "Ada Lovelace", BIRTHDATE, null, null, ref(42L), null))
                .isInstanceOf(InvalidPersonException.class);
    }

    @Test
    void personWhoIsTheirOwnChildIsRejected() {
        assertThatThrownBy(() -> new PersonDTO(42L, "Ada Lovelace", BIRTHDATE, null, null, null,
                List.of(ref(77L), ref(42L))))
                .isInstanceOf(InvalidPersonException.class);
    }

    @Test
    void personWithoutIdIsRejected() {
        assertThatThrownBy(() -> new PersonDTO(null, "Ada Lovelace", BIRTHDATE, null, null, null, null))
                .isInstanceOf(InvalidPersonException.class);
    }

    @Test
    void referenceWithIdBecomesIncompletePerson() {
        Person person = ref(7L).toPerson();

        assertThat(person.getId()).isEqualTo(7);
        assertThat(person.getName()).isNull();
    }

    /** JSON such as {@code "parent1": {}} or {@code "parent1": { "id": null }}. */
    @Test
    void referenceWithoutIdIsRejected() {
        assertThatThrownBy(() -> ref(null))
                .isInstanceOf(InvalidPersonException.class);
    }

    @Test
    void fromConvertsAllValuesWithRelationsSortedById() {
        Person person = new Person(42, "Ada Lovelace", BIRTHDATE, new Person(2), new Person(1), new Person(99),
                List.of(new Person(79), new Person(77), new Person(78)));

        PersonDTO dto = PersonDTO.from(person);

        assertThat(dto).isEqualTo(new PersonDTO(42L, "Ada Lovelace", BIRTHDATE,
                ref(1L), ref(2L), ref(99L), List.of(ref(77L), ref(78L), ref(79L))));
    }

    @Test
    void fromGivesUnknownValuesAsNullAndNoChildrenAsEmptyList() {
        PersonDTO dto = PersonDTO.from(new Person(99));

        assertThat(dto).isEqualTo(new PersonDTO(99L, null, null, null, null, null, List.of()));
    }

    @Test
    void fromWithOneParentFillsParent1() {
        Person person = new Person(42);
        person.addParent(new Person(1));

        PersonDTO dto = PersonDTO.from(person);

        assertThat(dto.parent1()).isEqualTo(ref(1L));
        assertThat(dto.parent2()).isNull();
    }

    @Test
    void fromAndToPersonGiveTheSameRecord() {
        PersonDTO original = new PersonDTO(42L, "Ada Lovelace", BIRTHDATE,
                ref(1L), ref(2L), ref(99L), List.of(ref(77L), ref(78L)));

        assertThat(PersonDTO.from(original.toPerson())).isEqualTo(original);
    }

    private static PersonReferenceDTO ref(Long id) {
        return new PersonReferenceDTO(id);
    }
}
