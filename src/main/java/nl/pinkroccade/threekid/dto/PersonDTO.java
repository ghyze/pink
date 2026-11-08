package nl.pinkroccade.threekid.dto;

import nl.pinkroccade.threekid.domain.InvalidPersonException;
import nl.pinkroccade.threekid.domain.Person;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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

    public PersonDTO {
        if (id == null) {
            throw new InvalidPersonException("A person must have an id.");
        }
        if (children != null && children.stream().anyMatch(Objects::isNull)) {
            throw new InvalidPersonException("The list of children cannot contain null.");
        }
        if (refersTo(parent1, id) || refersTo(parent2, id) || refersTo(partner, id)
                || (children != null && children.stream().anyMatch(child -> refersTo(child, id)))) {
            throw new InvalidPersonException("Person " + id + " cannot be their own parent, partner or child.");
        }
    }

    private static boolean refersTo(PersonReferenceDTO reference, Long id) {
        return reference != null && reference.id().equals(id);
    }

    /**
     * Converts a stored person for the response. Related people are given as ID references, sorted by ID so the
     * output is always the same. A relation we know nothing about is {@code null}; no children is an empty list.
     */
    public static PersonDTO from(Person person) {
        List<PersonReferenceDTO> parents = toReferences(person.getParents());
        return new PersonDTO(person.getId(),
                person.getName().orElse(null),
                person.getBirthDate().orElse(null),
                parents.size() > 0 ? parents.get(0) : null,
                parents.size() > 1 ? parents.get(1) : null,
                person.getPartner().map(partner -> new PersonReferenceDTO(partner.getId())).orElse(null),
                toReferences(person.getChildren()));
    }

    private static List<PersonReferenceDTO> toReferences(Collection<Person> people) {
        return people.stream()
                .map(Person::getId)
                .sorted()
                .map(PersonReferenceDTO::new)
                .toList();
    }

    /**
     * Converts this record to a {@link Person}. This is where {@code null} from the JSON stops: values that are left
     * out are not set, and relations that are left out are not added.
     */
    public Person toPerson(){
        Person person = new Person(id);
        if (name != null) {
            person.setName(name);
        }
        if (birthDate != null) {
            person.setBirthDate(birthDate);
        }
        Stream.of(parent1, parent2)
                .filter(Objects::nonNull)
                .map(PersonReferenceDTO::toPerson)
                .forEach(person::addParent);
        if (partner != null) {
            person.setPartner(partner.toPerson());
        }
        if (children != null) {
            children.stream().map(PersonReferenceDTO::toPerson).forEach(person::addChild);
        }
        return person;
    }
}
