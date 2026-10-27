---
sessionId: session-261027-155134-1g02
---

# Nuvarande Tillstånd

### Översikt

Projektet i `spring`-modulen är en Spring Boot 4.1.2-demo med Java 27, PostgreSQL, JPA/Hibernate, och Thymeleaf. Det
innehåller två entiteter — `Course` och `Student` — med en ManyToMany-relation.

### Vad som finns idag

| Komponent           | Kurs                                        | Student                     |
|---------------------|---------------------------------------------|-----------------------------|
| Entitet (`@Entity`) | ✅ `Course.java`                            | ✅ `Student.java`           |
| Repository          | ✅ `CourseRepository.java`                  | ✅ `StudentRepository.java` |
| REST-kontroller     | ✅ `CourseController.java` (GET)            | ❌ saknas                   |
| Webbkontroller      | ✅ `CourseWebController.java` (list + save) | ❌ saknas                   |
| Thymeleaf-template  | ✅ `courses.html`                           | ❌ saknas                   |
| Service-lager       | ❌ saknas                                   | ❌ saknas                   |
| DTO:er              | ❌ saknas (entiteter exponeras direkt)      | ❌ saknas                   |
| Validering          | ❌ saknas                                   | ❌ saknas                   |

### Problemer

1. **Studenthantering saknas helt** — ingen controller, ingen template, ingen UI för att hantera studenter.
2. **JPA-entiteter exponeras direkt** i REST API och Thymeleaf — bryter mot Spring Boot bästa praxis.
3. **Inget service-lager** — all logik finns i kontrollerna.
4. **Ingen input-validering** — ingen `@Valid` eller Bean Validation-annotationer.
5. **`courses.html` visar inte studenter** — trots att Course har en `students`-samling.
6. **`CommandLineRunner`** skapar seed-data men sparar student2 utan att koppla den korrekt (den läggs till i course men
   course sparas igen — kan förbättras).

# Förslag på Förbättringar

### 1. Lägg till studenthantering (ny funktionalitet)

**Nya filer:**

- `StudentController.java` — REST API för studenter (GET alla, GET by ID, POST skapa, PUT uppdatera, DELETE ta bort)
- `StudentWebController.java` — Webbkontroller för studentvyer
- `templates/students.html` — Thymeleaf-template för att lista, lägga till och ta bort studenter

**Funktioner:**

- Lista alla studenter med deras kurser
- Lägga till ny student
- Tilldela student till kurs (och tvärtom)
- Ta bort student

### 2. Införa DTO-mönstret (bästa praxis)

**Nya filer:**

- `CourseDto.java` — record för att returnera kursdata i REST API
- `StudentDto.java` — record för att returnera studentdata i REST API
- `CourseRequest.java` — record med `@Valid`-annotationer för att ta emot POST/PUT
- `StudentRequest.java` — record med `@Valid`-annotationer för att ta emot POST/PUT

**Ändringar:**

- `CourseController.java` — mappa mellan DTO:er och entiteter
- `StudentController.java` — samma mönster

### 3. Införa service-lager (arkitektur)

**Nya filer:**

- `CourseService.java` — `@Service` med `@Transactional` för kursrelaterad logik
- `StudentService.java` — `@Service` med `@Transactional` för studentrelaterad logik

**Ändringar:**

- `CourseController.java` och `CourseWebController.java` — injicera CourseService istället för CourseRepository direkt
- `StudentController.java` och `StudentWebController.java` — injicera StudentService

### 4. Förbättra courses.html-template

**Ändringar i `templates/courses.html`:**

- Lägg till kolumn för studenter i kurstabellen
- Visa studentnamn som länkar till studentdetailsida
- Lägg till möjlighet att tilldela studenter till kurs från kursvyn

### 5. Lägg till navigation

**Ny fil:**

- `templates/fragments/nav.html` — gemensam navigationsmeny

**Ändringar:**

- `courses.html` och `students.html` — inkludera navigation

### 6. Förbättra seed-data

**Ändringar i `Application.java`:**

- Använd CourseService och StudentService för att skapa seed-data
- Lägg till fler exempelstudenter och kurser för en mer realistisk demo

### 7. Globalt felhantering (enkel)

**Ny fil:**

- `GlobalExceptionHandler.java` — `@RestControllerAdvice` som hanterar grundläggande fel (t.ex. entity not found,
  valideringsfel) med enkla HTTP-statuskoder.

### Prioritering

1. **Hög prioritet** — Studenthantering (1), service-lager (3), DTO:er (2)
2. **Medel prioritet** — Navigation (5), förbättrad template (4), seed-data (6)
3. **Låg prioritet** — Felhantering (7)

# Delivery Steps

###  * Step 1: Inför service-lager och DTO:er

Skapa service-lager och enkla DTO:er som grund för resten.

- Skapa `CourseService.java` med `@Service` och `@Transactional`: metoder för `findAll`, `findById`, `save`, `delete`.
- Skapa `StudentService.java` med samma mönster.
- Skapa enkla DTO-record: `CourseDto`, `StudentDto`, `CourseRequest` (med `@NotBlank` på `title`), `StudentRequest` (med
  `@NotBlank` på `name`).
- Uppdatera `CourseController.java` och `CourseWebController.java` att använda `CourseService` och DTO:er.
- Uppdatera `Application.java` seed-data att använda service-lagret.

### Step 2: Implementera studenthantering

Lägg till REST API, webbkontroller och template för studenter.

- Skapa `StudentController.java` med REST-endpoints: GET `/api/students`, GET `/api/students/{id}`, POST, PUT, DELETE.
- Skapa `StudentWebController.java` med GET för att visa studentlista och POST för att spara.
- Skapa `templates/students.html` med tabell som listar studenter och deras kurser, samt formulär för ny student.
- Båda kontrollerna använder `StudentService` och DTO:er.

### Step 3: Navigation och förbättrad kursvy

Lägg till gemensam navigation och visa studenter i kursvyn.

- Skapa `templates/fragments/nav.html` med länkar till "Kurser" och "Studenter".
- Uppdatera `courses.html` för att inkludera navigation och visa studentnamn i en kolumn.
- Uppdatera `students.html` för att inkludera navigation.
- Lägg till länkar mellan kurs och studentvyer.

### Step 4: Enkel felhantering

Lägg till grundläggande felhantering.

- Skapa `GlobalExceptionHandler.java` med `@RestControllerAdvice` som hanterar `EntityNotFoundException` och
  valideringsfel med enkla HTTP-statuskoder.
- Lägg till `EntityNotFoundException` exception-klass.
