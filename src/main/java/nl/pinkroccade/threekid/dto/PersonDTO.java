package nl.pinkroccade.threekid.dto;

import nl.pinkroccade.threekid.domain.Person;

import java.time.LocalDate;
import java.util.List;

/**
 * Person record as received on and returned from the API.
 * Relations to other people are expressed as ID references, so they may point to people we have not seen yet.
 */
public record PersonDTO(
        Long id,
        String name,
        LocalDate birthDate,
        PersonReferenceDTO parent1,
        PersonReferenceDTO parent2,
        PersonReferenceDTO partner,
        List<PersonReferenceDTO> children
) {

    public Person toPerson(){
        return new Person(id, name, birthDate, parent1.toPerson(), parent2.toPerson(), partner.toPerson(), children.stream().map(PersonReferenceDTO::toPerson).toList());
    }
}
