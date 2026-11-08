package nl.pinkroccade.threekid.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A person and their relations. A person can be incomplete: we may only know their ID, because someone else referred
 * to them. Values we don't know are returned as an empty {@link Optional}; relations we don't know are empty sets.
 * {@code null} is never accepted or returned.
 */
public class Person {
    private final long id;
    private String name;
    private LocalDate birthDate;
    private final Set<Person> parents = new HashSet<>();
    private Person partner;
    private final Set<Person> children = new HashSet<>();

    public Person(long id){
        this.id = id;
    }

    public long getId(){
        return id;
    }

    public Optional<String> getName(){
        return Optional.ofNullable(name);
    }

    public Optional<LocalDate> getBirthDate(){
        return Optional.ofNullable(birthDate);
    }

    public Collection<Person> getParents(){
        return Set.copyOf(parents);
    }

    public Optional<Person> getPartner(){
        return Optional.ofNullable(partner);
    }

    public Collection<Person> getChildren(){
        return Set.copyOf(children);
    }

    public void setName(String name){
        this.name = Objects.requireNonNull(name);
    }

    public void setBirthDate(LocalDate birthDate){
        this.birthDate = Objects.requireNonNull(birthDate);
    }

    /**
     * Replaces the name and birth date with those of the given person, including values the given person does not
     * have: those become unknown.
     */
    public void copyDetailsFrom(Person other){
        this.name = other.name;
        this.birthDate = other.birthDate;
    }

    public void addParent(Person parent){
        Objects.requireNonNull(parent);
        if (parents.size() == 2 && parents.stream().noneMatch(p -> p.getId() == parent.getId())){
            throw new InvalidPersonException("Person " + id + " already has two different parents. Cannot add parent " + parent.getId() + ".");
        }
        parents.remove(parent);
        parents.add(parent);
    }

    public void removeParent(Person parent){
        parents.remove(parent);
    }

    public void setPartner(Person partner){
        this.partner = Objects.requireNonNull(partner);
    }

    public void clearPartner(){
        this.partner = null;
    }

    public void addChild(Person child){
        Objects.requireNonNull(child);
        children.remove(child);
        children.add(child);
    }

    public void removeChild(Person child){
        children.remove(child);
    }

    public boolean potentiallyMatches(){
        return this.partner != null &&
                this.children.size() == 3 &&
                allChildrenHavePartnerAsParent();
    }

    /**
     * @return whether at least one child is younger than 18 on the given day; a child without a known birth date
     * does not count, because we cannot tell
     */
    public boolean hasChildUnder18(LocalDate today){
        return children.stream()
                .flatMap(child -> child.getBirthDate().stream())
                .anyMatch(birthDate -> birthDate.plusYears(18).isAfter(today));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return id == person.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    private boolean allChildrenHavePartnerAsParent(){
        return children.stream().filter(c -> c.hasParent(partner)).count() == children.size();
    }

    private boolean hasParent(Person parent){
        return parents.stream().anyMatch(p -> p.equals(parent));
    }
}
