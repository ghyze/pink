package nl.pinkroccade.threekid.dto;

/**
 * Reference to another person by ID, as it appears in the JSON: {@code { "id": 1 }}.
 */
public record PersonReferenceDTO(Long id) {
}
