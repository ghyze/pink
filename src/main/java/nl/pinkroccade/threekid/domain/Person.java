package nl.pinkroccade.threekid.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class Person {
    private final long id;
    private String name;
    private LocalDate birthDate;
    private Set<Person> parents = new HashSet<>();
    private Person partner;
    private Set<Person> children = new HashSet<>();

    public Person(long id, String name, LocalDate birthDate, Person parent1, Person parent2, Person partner, List<Person> children) {
        this.id = id;
        this.name = name;
        this.birthDate = birthDate;
        parents.addAll(Stream.of(parent1, parent2).filter(Objects::nonNull).toList());
        this.partner = partner;
        if (children != null) {
            this.children.addAll(children);
        }
    }

    public Person(long id){
        this.id = id;
    }

    public long getId(){
        return id;
    }

    public String getName(){
        return name;
    }

    public LocalDate getBirthDate(){
        return birthDate;
    }

    public Collection<Person> getParents(){
        return Set.copyOf(parents);
    }

    public Person getPartner(){
        return partner;
    }

    public Collection<Person> getChildren(){
        return Set.copyOf(children);
    }

    public void setName(String name){
        this.name = name;
    }

    public void setBirthDate(LocalDate birthDate){
        this.birthDate = birthDate;
    }

    public void addParent(Person parent){
        if (parent == null){
            return;
        }
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
        this.partner = partner;
    }

    public void addChild(Person child){
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
