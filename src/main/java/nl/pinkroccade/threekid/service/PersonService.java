package nl.pinkroccade.threekid.service;

import nl.pinkroccade.threekid.domain.Person;
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
    public List<Person> addPerson(PersonDTO person) {
        // TODO store the person and evaluate the pattern
        // Before storing, validate if it can be stored. We don't want rollbacks.
        return List.of();
    }
}
