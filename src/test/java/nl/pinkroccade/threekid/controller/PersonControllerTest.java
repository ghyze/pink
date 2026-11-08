package nl.pinkroccade.threekid.controller;

import nl.pinkroccade.threekid.domain.InvalidPersonException;
import nl.pinkroccade.threekid.domain.Person;
import nl.pinkroccade.threekid.dto.PersonDTO;
import nl.pinkroccade.threekid.dto.PersonReferenceDTO;
import nl.pinkroccade.threekid.service.PersonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonController.class)
class PersonControllerTest {

    private static final String ADA_JSON = """
            {
              "id": 42,
              "name": "Ada Lovelace",
              "birthDate": "1815-12-10",
              "parent1": { "id": 1 },
              "parent2": { "id": 2 },
              "partner": { "id": 99 },
              "children": [{ "id": 77 }, { "id": 78 }]
            }
            """;

    private static final PersonDTO ADA = new PersonDTO(
            42L, "Ada Lovelace", LocalDate.of(1815, 12, 10),
            new PersonReferenceDTO(1L), new PersonReferenceDTO(2L), new PersonReferenceDTO(99L),
            List.of(new PersonReferenceDTO(77L), new PersonReferenceDTO(78L)));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonService personService;

    @Test
    void returns444WhenNobodyMatches() throws Exception {
        when(personService.addPerson(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/people").contentType(MediaType.APPLICATION_JSON).content(ADA_JSON))
                .andExpect(status().is(444))
                .andExpect(content().string(""));

        verify(personService).addPerson(ADA);
    }

    @Test
    void returns200WithMatchingPeople() throws Exception {
        when(personService.addPerson(any())).thenReturn(List.of(ADA));

        mockMvc.perform(post("/api/v1/people").contentType(MediaType.APPLICATION_JSON).content(ADA_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].birthDate").value("1815-12-10"))
                .andExpect(jsonPath("$[0].parent1.id").value(1))
                .andExpect(jsonPath("$[0].parent2.id").value(2))
                .andExpect(jsonPath("$[0].partner.id").value(99))
                .andExpect(jsonPath("$[0].children[0].id").value(77))
                .andExpect(jsonPath("$[0].children[1].id").value(78));
    }

    @Test
    void returnsIncompleteMatchingPeopleWithUnknownValuesAsNull() throws Exception {
        when(personService.addPerson(any())).thenReturn(List.of(PersonDTO.from(new Person(99))));

        mockMvc.perform(post("/api/v1/people").contentType(MediaType.APPLICATION_JSON).content(ADA_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(99))
                .andExpect(jsonPath("$[0].name").doesNotExist())
                .andExpect(jsonPath("$[0].children").isEmpty());
    }

    @Test
    void returns400WhenIdIsMissing() throws Exception {
        String withoutId = """
                { "name": "Ada Lovelace", "birthDate": "1815-12-10" }
                """;

        mockMvc.perform(post("/api/v1/people").contentType(MediaType.APPLICATION_JSON).content(withoutId))
                .andExpect(status().isBadRequest());

        verify(personService, never()).addPerson(any());
    }

    @Test
    void acceptsMissingRelations() throws Exception {
        when(personService.addPerson(any())).thenReturn(List.of());
        String withoutRelations = """
                { "id": 42, "name": "Ada Lovelace", "birthDate": "1815-12-10" }
                """;

        mockMvc.perform(post("/api/v1/people").contentType(MediaType.APPLICATION_JSON).content(withoutRelations))
                .andExpect(status().is(444));

        verify(personService).addPerson(new PersonDTO(42L, "Ada Lovelace", LocalDate.of(1815, 12, 10),
                null, null, null, null));
    }

    @Test
    void returns400WhenReferenceHasNoId() throws Exception {
        String referenceWithoutId = """
                { "id": 42, "parent1": {} }
                """;

        mockMvc.perform(post("/api/v1/people").contentType(MediaType.APPLICATION_JSON).content(referenceWithoutId))
                .andExpect(status().isBadRequest());

        verify(personService, never()).addPerson(any());
    }

    @Test
    void returns400WhenChildIsNull() throws Exception {
        String nullChild = """
                { "id": 42, "children": [{ "id": 77 }, null] }
                """;

        mockMvc.perform(post("/api/v1/people").contentType(MediaType.APPLICATION_JSON).content(nullChild))
                .andExpect(status().isBadRequest());

        verify(personService, never()).addPerson(any());
    }

    @Test
    void returns400WhenPersonRefersToThemselves() throws Exception {
        String selfReference = """
                { "id": 42, "partner": { "id": 42 } }
                """;

        mockMvc.perform(post("/api/v1/people").contentType(MediaType.APPLICATION_JSON).content(selfReference))
                .andExpect(status().isBadRequest());

        verify(personService, never()).addPerson(any());
    }

    @Test
    void returns400WhenPersonIsInvalid() throws Exception {
        when(personService.addPerson(any())).thenThrow(new InvalidPersonException("third parent"));

        mockMvc.perform(post("/api/v1/people").contentType(MediaType.APPLICATION_JSON).content(ADA_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("third parent"));
    }
}
