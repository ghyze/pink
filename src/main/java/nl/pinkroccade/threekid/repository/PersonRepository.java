package nl.pinkroccade.threekid.repository;

import nl.pinkroccade.threekid.domain.Person;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class PersonRepository {

    private final Map<Long, Person> repository = new ConcurrentHashMap<>();

    public void addOrUpdate(Person person){
        // TODO: if person already exists, remove relations.
        repository.put(person.getId(), person);

        // TODO: set relations.
    }
}
