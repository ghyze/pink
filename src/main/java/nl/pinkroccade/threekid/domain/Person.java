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

}
