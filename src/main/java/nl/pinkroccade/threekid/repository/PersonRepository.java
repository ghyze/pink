package nl.pinkroccade.threekid.repository;

import nl.pinkroccade.threekid.domain.InvalidPersonException;
import nl.pinkroccade.threekid.domain.Person;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Keeps one {@link Person} object per ID, and keeps the relations between people bidirectional.
 * <p>
 * All access goes through a read/write lock: an update changes several people at once, and nobody may see it
 * half done.
 */
@Repository
public class PersonRepository {

    private final Map<Long, Person> repository = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Stores the given person, or updates the stored person with the same ID in place. The record replaces all
     * relations of that person, including relations that earlier records of other people established: a relation
     * the record does not mention is broken on both sides. People that are referred to but not yet known are stored
     * as incomplete people.
     *
     * @throws InvalidPersonException if the record would give one of its children a third parent; nothing is changed
     */
    public void addOrUpdate(Person person) {
        lock.writeLock().lock();
        try {
            validate(person);

            Person stored = getOrCreate(person.getId());
            stored.setName(person.getName());
            stored.setBirthDate(person.getBirthDate());
            replaceParents(stored, resolve(person.getParents()));
            replaceChildren(stored, resolve(person.getChildren()));
            replacePartner(stored, person.getPartner() == null ? null : getOrCreate(person.getPartner().getId()));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @return the stored person, or {@code null} if this ID is unknown
     */
    public Person findPerson(long id) {
        lock.readLock().lock();
        try {
            return repository.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Checks everything that could fail, before anything is changed. */
    private void validate(Person person) {
        for (Person child : person.getChildren()) {
            Person storedChild = repository.get(child.getId());
            if (storedChild == null) {
                continue;
            }
            long otherParents = storedChild.getParents().stream().filter(p -> p.getId() != person.getId()).count();
            if (otherParents >= 2) {
                throw new InvalidPersonException("Person " + child.getId()
                        + " already has two different parents. Cannot add parent " + person.getId() + ".");
            }
        }
    }

    private Person getOrCreate(long id) {
        return repository.computeIfAbsent(id, Person::new);
    }

    private Set<Person> resolve(Collection<Person> references) {
        return references.stream().map(p -> getOrCreate(p.getId())).collect(Collectors.toSet());
    }

    private void replaceParents(Person person, Set<Person> parents) {
        for (Person oldParent : person.getParents()) {
            if (!parents.contains(oldParent)) {
                person.removeParent(oldParent);
                oldParent.removeChild(person);
            }
        }
        for (Person parent : parents) {
            person.addParent(parent);
            parent.addChild(person);
        }
    }

    private void replaceChildren(Person person, Set<Person> children) {
        for (Person oldChild : person.getChildren()) {
            if (!children.contains(oldChild)) {
                person.removeChild(oldChild);
                oldChild.removeParent(person);
            }
        }
        for (Person child : children) {
            person.addChild(child);
            child.addParent(person);
        }
    }

    private void replacePartner(Person person, Person partner) {
        Person oldPartner = person.getPartner();
        if (oldPartner != null && !oldPartner.equals(partner)) {
            oldPartner.setPartner(null);
        }
        person.setPartner(partner);
        if (partner != null) {
            Person partnersOldPartner = partner.getPartner();
            if (partnersOldPartner != null && !partnersOldPartner.equals(person)) {
                partnersOldPartner.setPartner(null);
            }
            partner.setPartner(person);
        }
    }
}
