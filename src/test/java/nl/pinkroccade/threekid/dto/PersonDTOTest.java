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

    private static PersonReferenceDTO ref(Long id) {
        return new PersonReferenceDTO(id);
    }
}
