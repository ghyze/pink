package nl.pinkroccade.threekid.dto;

import nl.pinkroccade.threekid.domain.InvalidPersonException;
import nl.pinkroccade.threekid.domain.Person;

/**
 * Reference to another person by ID, as it appears in the JSON: {@code { "id": 1 }}.
 * A reference without an ID, such as {@code {}} or {@code { "id": null }}, is rejected.
 */
public record PersonReferenceDTO(Long id) {

    public PersonReferenceDTO {
        if (id == null) {
            throw new InvalidPersonException("A reference to a person must have an id.");
        }
    }

    public Person toPerson(){
        return new Person(id);
    }
}
