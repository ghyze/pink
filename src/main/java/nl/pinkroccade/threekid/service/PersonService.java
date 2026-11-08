package nl.pinkroccade.threekid.service;

import nl.pinkroccade.threekid.dto.PersonDTO;
import nl.pinkroccade.threekid.repository.PersonRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class PersonService {

    private final PersonRepository repository;
    private final Clock clock;

    public PersonService(PersonRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Stores (or replaces) the given person and returns all people currently matching the three-kid pattern.
     *
     * @return the matching people; empty if nobody matches
     */
    public List<PersonDTO> addPerson(PersonDTO person) {
        repository.addOrUpdate(person.toPerson());
        LocalDate today = LocalDate.now(clock);
        return repository.findPotentialMatches(match -> match.hasChildUnder18(today), PersonDTO::from);
    }
}
