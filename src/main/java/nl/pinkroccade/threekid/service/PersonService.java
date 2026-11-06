package nl.pinkroccade.threekid.service;

import nl.pinkroccade.threekid.dto.PersonDTO;
import nl.pinkroccade.threekid.repository.PersonRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PersonService {

    private final PersonRepository repository;

    public PersonService(PersonRepository repository) {
        this.repository = repository;
    }

    /**
     * Stores (or replaces) the given person and returns all people currently matching the three-kid pattern.
     *
     * @return the matching people; empty if nobody matches
     */
    public List<PersonDTO> addPerson(PersonDTO person) {
        // TODO store the person and evaluate the pattern
        return List.of();
    }
}
