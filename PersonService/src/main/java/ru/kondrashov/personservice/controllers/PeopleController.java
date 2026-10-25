package ru.kondrashov.personservice.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.kondrashov.personservice.controllers.dto.PersonRequestDTO;
import ru.kondrashov.personservice.controllers.dto.PersonResponseDTO;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.exception.InvalidPageRequestException;
import ru.kondrashov.personservice.services.PeopleService;
import ru.kondrashov.personservice.utils.PeopleMapping;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@RestController
@RequestMapping(value = "/v1/people", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Api(description = "Controller which provides methods for operations with people", tags = {"PEOPLE"})
public class PeopleController {

    private final PeopleService peopleService;
    private final PeopleMapping peopleMapping;
    private final Logger logger;

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;
    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";
    public static final String TOTAL_PAGES_HEADER = "X-Total-Pages";
    public static final String PAGE_HEADER = "X-Page";
    public static final String PAGE_SIZE_HEADER = "X-Page-Size";
    private static final Sort PEOPLE_ORDER = Sort.by("lastName", "firstName", "id");

    @GetMapping
    @ApiOperation("Find people, one page at a time, ordered by last name, first name and id")
    public ResponseEntity<List<PersonResponseDTO>> getPeople(
            @ApiParam("Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @ApiParam("Page size, 1.." + MAX_PAGE_SIZE) @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people\""+" received a GET request from the server "+header.getHost()+" with content type "+header.getContentType());
        if (page < 0) {
            throw new InvalidPageRequestException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPageRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Page<Person> people = peopleService.getAll(PageRequest.of(page, size, PEOPLE_ORDER));
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, String.valueOf(people.getTotalElements()))
                .header(TOTAL_PAGES_HEADER, String.valueOf(people.getTotalPages()))
                .header(PAGE_HEADER, String.valueOf(people.getNumber()))
                .header(PAGE_SIZE_HEADER, String.valueOf(people.getSize()))
                .body(people.getContent()
                        .stream()
                        .map(peopleMapping::mapToPersonResponseDTO)
                        .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation("Find person by ID")
    public PersonResponseDTO getPerson(@ApiParam("ID of the person. Cannot to be null or empty") @PathVariable UUID id, @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{id}\""+" received a GET request from the server "+header.getHost()+" with content type "+header.getContentType());
        return peopleMapping.mapToPersonResponseDTO(peopleService.get(id));
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    @ApiOperation("Save a new person")
    public void createPerson(@ApiParam("new person to be save") @RequestBody @Valid PersonRequestDTO personRequestDTO, @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people\""+" received a POST request from the server "+header.getHost()+" with content type "+header.getContentType());
        peopleService.save(peopleMapping.mapToPerson(personRequestDTO));
    }

    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @ApiOperation("Update a person")
    public void updatePerson(@ApiParam("new person to be update") @RequestBody @Valid PersonRequestDTO personRequestDTO,
                             @ApiParam("ID of the person. Cannot to be null or empty") @PathVariable("id") UUID id, @RequestHeader HttpHeaders header) {
        logger.info("The controller "+"\"/v1/people/{id}\""+" received a PUT request from the server "+header.getHost()+" with content type "+header.getContentType());
        peopleService.update(id, peopleMapping.mapToPerson(personRequestDTO));
    }

    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiOperation("Delete a person")
    public void deletePerson(@ApiParam("ID of the person. Cannot to be null or empty") @PathVariable("id") UUID id, @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{id}\""+" received a DELETE request from the server "+header.getHost()+" with content type "+header.getContentType());
        peopleService.delete(id);

    }
}
