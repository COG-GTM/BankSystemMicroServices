package ru.kondrashov.personservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.kondrashov.personservice.controllers.dto.PersonRequestDTO;
import ru.kondrashov.personservice.controllers.dto.PersonResponseDTO;
import ru.kondrashov.personservice.services.PeopleService;
import ru.kondrashov.personservice.utils.PeopleMapping;

import javax.validation.Valid;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;


@RestController
@RequestMapping(value = "/v1/people", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "PEOPLE", description = "Controller which provides methods for operations with people")
public class PeopleController {

    private final PeopleService peopleService;
    private final PeopleMapping peopleMapping;
    private final Logger logger;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Find people")
    public Collection<PersonResponseDTO> getPeople(@RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people\""+" received a GET request from the server "+header.getHost()+" with content type "+header.getContentType());
        return peopleService.getAll()
                .stream()
                .map(peopleMapping::mapToPersonResponseDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Find person by ID")
    public PersonResponseDTO getPerson(@Parameter(description = "ID of the person. Cannot to be null or empty") @PathVariable UUID id, @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{id}\""+" received a GET request from the server "+header.getHost()+" with content type "+header.getContentType());
        return peopleMapping.mapToPersonResponseDTO(peopleService.get(id));
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a new person")
    public void createPerson(@Parameter(description = "new person to be save") @RequestBody @Valid PersonRequestDTO personRequestDTO, @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people\""+" received a POST request from the server "+header.getHost()+" with content type "+header.getContentType());
        peopleService.save(peopleMapping.mapToPerson(personRequestDTO));
    }

    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Update a person")
    public void updatePerson(@Parameter(description = "new person to be update") @RequestBody @Valid PersonRequestDTO personRequestDTO,
                             @Parameter(description = "ID of the person. Cannot to be null or empty") @PathVariable("id") UUID id, @RequestHeader HttpHeaders header) {
        logger.info("The controller "+"\"/v1/people/{id}\""+" received a PUT request from the server "+header.getHost()+" with content type "+header.getContentType());
        peopleService.update(id, peopleMapping.mapToPerson(personRequestDTO));
    }

    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a person")
    public void deletePerson(@Parameter(description = "ID of the person. Cannot to be null or empty") @PathVariable("id") UUID id, @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{id}\""+" received a DELETE request from the server "+header.getHost()+" with content type "+header.getContentType());
        peopleService.delete(id);

    }
}
