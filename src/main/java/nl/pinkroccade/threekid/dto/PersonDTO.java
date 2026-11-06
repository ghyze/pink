package nl.pinkroccade.threekid.dto;

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
}
