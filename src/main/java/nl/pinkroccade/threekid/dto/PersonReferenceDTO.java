package nl.pinkroccade.threekid.dto;

import nl.pinkroccade.threekid.domain.Person;

/**
 * Reference to another person by ID, as it appears in the JSON: {@code { "id": 1 }}.
 */
public record PersonReferenceDTO(Long id) {
    public Person toPerson(){
        return new Person(id);
    }
}
