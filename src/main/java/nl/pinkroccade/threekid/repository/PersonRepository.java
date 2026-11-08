package nl.pinkroccade.threekid.repository;

import nl.pinkroccade.threekid.domain.InvalidPersonException;
import nl.pinkroccade.threekid.domain.Person;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Predicate;
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
    private final Map<Long, Person> potentialMatches = new HashMap<>();
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
            Optional<Person> partner = person.getPartner().map(p -> getOrCreate(p.getId()));

            // Everyone whose partner, children or children's parents can change: the person, their old and new
            // relations, and the partner that the new partner leaves behind.
            Set<Person> affected = new HashSet<>();
            affected.add(stored);
            affected.addAll(relationsOf(stored));
            partner.flatMap(Person::getPartner).ifPresent(affected::add);

            stored.copyDetailsFrom(person);
            replaceParents(stored, resolve(person.getParents()));
            replaceChildren(stored, resolve(person.getChildren()));
            partner.ifPresentOrElse(p -> linkPartners(stored, p), () -> unlinkPartner(stored));

            affected.addAll(relationsOf(stored));
            affected.forEach(this::updatePotentialMatch);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Finds the potential matches (everyone who matches the pattern without the age check) that pass the given filter,
     * and converts them with the given mapper. Both run under the read lock, so they see the stored people in a
     * consistent state. The mapper must return a copy: stored people must not be used after this method returns.
     *
     * @return the converted matches, in no particular order
     */
    public <T> List<T> findPotentialMatches(Predicate<Person> filter, Function<Person, T> mapper) {
        lock.readLock().lock();
        try {
            return potentialMatches.values().stream()
                    .filter(filter)
                    .map(mapper)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Finds the stored person with the given ID and converts them with the given mapper, under the read lock. Like
     * for {@link #findPotentialMatches}, the mapper must return a copy: the stored person must not be used after this
     * method returns.
     *
     * @return the converted person, or empty if this ID is unknown
     */
    public <T> Optional<T> findPerson(long id, Function<Person, T> mapper) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(repository.get(id)).map(mapper);
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

    private static Set<Person> relationsOf(Person person) {
        Set<Person> relations = new HashSet<>(person.getParents());
        relations.addAll(person.getChildren());
        person.getPartner().ifPresent(relations::add);
        return relations;
    }

    private void updatePotentialMatch(Person person) {
        if (person.potentiallyMatches()) {
            potentialMatches.put(person.getId(), person);
        } else {
            potentialMatches.remove(person.getId());
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

    /** Makes the two people each other's partner, and releases the previous partner of both. */
    private void linkPartners(Person person, Person partner) {
        person.getPartner().filter(old -> !old.equals(partner)).ifPresent(Person::clearPartner);
        partner.getPartner().filter(old -> !old.equals(person)).ifPresent(Person::clearPartner);
        person.setPartner(partner);
        partner.setPartner(person);
    }

    /** Ends the partnership of the person on both sides, if they have one. */
    private void unlinkPartner(Person person) {
        person.getPartner().ifPresent(Person::clearPartner);
        person.clearPartner();
    }
}
