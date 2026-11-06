package nl.pinkroccade.threekid.controller;

import nl.pinkroccade.threekid.dto.PersonDTO;
import nl.pinkroccade.threekid.service.PersonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/people")
public class PersonController {

    /** Non-standard status (nginx "No Response") required by the specification; not in {@code HttpStatus}. */
    static final int NO_RESPONSE = 444;

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @PostMapping
    public ResponseEntity<List<PersonDTO>> addPerson(@RequestBody PersonDTO person) {
        List<PersonDTO> matches = personService.addPerson(person);
        if (matches.isEmpty()) {
            return ResponseEntity.status(NO_RESPONSE).build();
        }
        return ResponseEntity.ok(matches);
    }
}
