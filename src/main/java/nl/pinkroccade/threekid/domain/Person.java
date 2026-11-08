package nl.pinkroccade.threekid.domain;

import java.time.LocalDate;
import java.util.List;

public class Person {
    private long id;
    private String name;
    private LocalDate birthdate;
    private Person parent1;
    private Person parent2;
    private Person partner;
    private List<Person> children;

    public Person(long id, String name, LocalDate birthdate, Person parent1, Person parent2, Person partner, List<Person> children) {
        this.id = id;
        this.name = name;
        this.birthdate = birthdate;
        this.parent1 = parent1;
        this.parent2 = parent2;
        this.partner = partner;
        this.children = children;
    }

    public Person(long id){
        this.id = id;
    }
}
