package nl.pinkroccade.threekid.dto;

import nl.pinkroccade.threekid.domain.InvalidPersonException;
import nl.pinkroccade.threekid.domain.Person;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

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

    public PersonDTO {
        if (id == null) {
            throw new InvalidPersonException("A person must have an id.");
        }
        if (children != null && children.stream().anyMatch(Objects::isNull)) {
            throw new InvalidPersonException("The list of children cannot contain null.");
        }
    }

    public Person toPerson(){
        return new Person(id,
                name,
                birthDate,
                toPerson(parent1),
                toPerson(parent2),
                toPerson(partner),
                children == null? List.of() : children.stream().map(PersonReferenceDTO::toPerson).toList());
    }

    private Person toPerson(PersonReferenceDTO reference){
        return reference == null ? null : reference.toPerson();
    }
}
