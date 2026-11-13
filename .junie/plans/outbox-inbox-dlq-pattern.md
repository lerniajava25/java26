---
sessionId: session-261112-191841-86ht
---

# Requirements

### Overview & Goals

Implementera **outbox pattern** i spring-modulen och **inbox pattern** i mqreciver-modulen för att garantera att
meddelanden som skickas via RabbitMQ inte går förlorade och bearbetas exakt en gång. Dessutom implementeras **dead
letter queue (DLQ)** för att fånga meddelanden som misslyckas efter max antal försök.

### User Stories

- As a system, I want to store outgoing messages in a database table before publishing to RabbitMQ, so that no message
  is lost if the publish fails.
- As a consumer, I want to track received messages in an inbox table, so that duplicate messages are ignored and
  processed exactly once.
- As an operator, I want failed messages to end up in a dead letter queue after retry attempts, so that they can be
  inspected and reprocessed manually.

### Functional Requirements

1. **Outbox (spring-modul)**:
    - När en Course sparas, ska en utbox-post skrivas till databasen **inom samma transaktion** som Course-sparningen.
    - En scheduled poller ska regelbundet läsa opublikierade utbox-poster och publicera dem till RabbitMQ.
    - Efter lyckad publicering, markeras utbox-posten som publicerad.

2. **Inbox (mqreciver-modul)**:
    - Varje inkommande meddelande ska loggas i en inbox-tabell med ett unikt meddelande-ID innan bearbetning.
    - Om ett meddelande med samma ID redan finns i inbox, ska det ignoreras (idempotens).
    - mqreciver behöver databasstöd (Spring Data JPA + PostgreSQL/H2).

3. **Dead Letter Queue**:
    - Huvudqueuen `course-created` ska konfigureras med `x-dead-letter-exchange` och `x-dead-letter-routing-key`.
    - Meddelanden som misslyckas efter max 3 försök ska skickas till en DLQ (`course-created-dlq`).
    - DLQ-exchange och DLQ-queue ska skapas i RabbitMQ-konfigurationen.

### Non-Functional Requirements

- Outbox-tabellen ska använda en unik ID (UUID) per meddelande för att möjliggöra idempotens.
- Scheduled pollern ska köras var 5:e sekund (konfigurerbart).
- DLQ ska vara durable (beständig vid RabbitMQ-omstart).

### Step Status

### ✓ Step 1: Implementera outbox i spring-modulen

### ✓ Step 2: Implementera DLQ i RabbitMQ-konfigurationen

### ✓ Step 3: Implementera inbox i mqreciver-modulen

### Scope

**In Scope:**

- Outbox entity, repository, service och scheduled poller i spring-modulen.
- Inbox entity, repository och idempotenslogik i mqreciver-modulen.
- DLQ-konfiguration i RabbitMQConfig.
- Modifiera CourseService för att skriva utbox-poster istället för att publicera direkt.
- Modifiera CourseConsumer för att använda inbox pattern.

**Out of Scope:**

- Manuell reprocessing från DLQ (endast lagring och granskning).
- Multi-tenant eller komplex routing.

# Technical Design

### Current Implementation

- **spring-modul**: `CourseService.save()` sparar Course och skickar direkt ett meddelande till RabbitMQ via
  `RabbitMessagingTemplate`. Om publiceringen misslyckas efter att Course sparats, går meddelandet förlorat.
  `RabbitMQConfig` definierar `course-exchange` (DirectExchange), `course-created` queue, och binding med routing key
  `course.created`.
- **mqreciver-modul**: `CourseConsumer` lyssnar på `course-created` queue via `@RabbitListener`. Ingen databaskoppling
  eller idempotenshantering finns.
- Ingen DLQ-konfiguration finns idag.

### Key Decisions

1. **Outbox via scheduled poller**: En `@Scheduled`-metod i spring-modulen läser opublikierade utbox-poster och
   publicerar dem. Enkelt att felsöka och testa.
2. **Outbox-tabell inom samma transaktion som Course**: Utbox-posten skrivs i `CourseService.save()` innan metoden
   returnerar, inom samma `@Transactional`-block. Detta garanterar att utbox-posten aldrig skrivs utan att Course också
   sparats.
3. **Inbox med unique constraint på message_id**: mqreciver får en inbox-tabell med ett unique constraint på
   `message_id` (UUID). Om samma meddelande anländer igen, kastas `DataIntegrityViolationException` och meddelandet
   ignoreras.
4. **DLQ via RabbitMQ arguments**: Huvudqueuen konfigureras med `x-dead-letter-exchange` och
   `x-dead-letter-routing-key`. En separat DLQ-queue och exchange skapas. Max 3 försök via `x-message-ttl` och
   retry-queue (eller enklare: nack utan requeue efter 3 försök via `x-max-redeliveries` — men RabbitMQ stöder inte
   detta direkt, så vi använder en enkel nack-till-DLQ-strategi).

### Proposed Changes

#### spring-modul

1. **Outbox entity** — ny JPA-entity `Outbox` med fält: `id` (UUID), `payload` (String/JSON), `message_type` (String),
   `published` (boolean), `created_at` (timestamp).
2. **OutboxRepository** — `ListCrudRepository<Outbox, UUID>` med metod `findAllByPublishedFalse()`.
3. **OutboxPublisher** — ny service med `@Scheduled(fixedDelay = 5000)` som läser opublikierade poster, publicerar till
   RabbitMQ, och markerar som publicerade.
4. **CourseService** — ändras för att skriva en Outbox-post istället för att anropa `messagingTemplate.send()` direkt.
5. **RabbitMQConfig** — läggs till DLQ-exchange, DLQ-queue, och DLQ-binding. Huvudqueuen får arguments för dead letter
   routing.

#### mqreciver-modul

1. **Lägg till Spring Data JPA + PostgreSQL-drivrutin** i `pom.xml`.
2. **Inbox entity** — ny JPA-entity `Inbox` med fält: `message_id` (UUID, unique), `payload` (String), `processed_at`
   (timestamp).
3. **InboxRepository** — `ListCrudRepository<Inbox, UUID>` med metod `existsById()`.
4. **CourseConsumer** — ändras för att:
    - Extrahera meddelande-ID från header (sätts av outbox publisher).
    - Kontrollera om meddelandet redan finns i inbox.
    - Om inte, spara i inbox och bearbeta.
    - Om ja, ignorera meddelandet.
5. **application.properties** — konfigurera datasource för PostgreSQL (eller H2 för utveckling).

### Data Models

#### Outbox (spring-modul)

```java
@Entity
public class Outbox {
    @Id
    private UUID id = UUID.randomUUID();
    private String payload;
    private String messageType;
    private boolean published = false;
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

#### Inbox (mqreciver-modul)

```java
@Entity
public class Inbox {
    @Id
    private UUID messageId;
    private String payload;
    private LocalDateTime processedAt = LocalDateTime.now();
}
```

### File Structure

**Nya filer i spring-modul:**

- `src/main/java/org/example/spring/entity/Outbox.java`
- `src/main/java/org/example/spring/repository/OutboxRepository.java`
- `src/main/java/org/example/spring/service/OutboxPublisher.java`

**Nya filer i mqreciver-modul:**

- `src/main/java/org/example/mqreciver/entity/Inbox.java`
- `src/main/java/org/example/mqreciver/repository/InboxRepository.java`
- `src/main/resources/application.properties` (datasource-konfiguration)

**Modifierade filer:**

- `spring/src/main/java/org/example/spring/service/CourseService.java` — skriva utbox-post istället för direkt
  publicering.
- `spring/src/main/java/org/example/spring/config/RabbitMQConfig.java` — lägga till DLQ-konfiguration.
- `mqreciver/src/main/java/org/example/mqreciver/CourseConsumer.java` — använda inbox pattern.
- `mqreciver/pom.xml` — lägga till Spring Data JPA och databasdrivrutin.

### Architecture Diagram

``` mermaid
graph LR
    subgraph spring
        CS[CourseService] -->|1 . save| DB[(PostgreSQL)]
        CS -->|2 . write| OUTBOX[Outbox Table]
        OP[OutboxPublisher] -->|3 . poll| OUTBOX
        OP -->|4 . publish| RMQ[RabbitMQ]
    end

    subgraph rabbitmq
        RMQ -->|route| Q[course-created Queue]
        Q -->|on fail x3| DLQ[DLQ Queue]
    end

    subgraph mqreciver
        Q -->|consume| CC[CourseConsumer]
        CC -->|5 . check| INBOX[Inbox Table]
        CC -->|6 . process| PROC[Business Logic]
        CC -->|7 . save| INBOX
    end

    DB2[(PostgreSQL)] -.-> INBOX
```

### Risks

- **Outbox poller fördröjning**: Meddelanden publiceras med max 5 sekunders fördröjning. Acceptabelt för de flesta use
  cases.
- **Transaktionsisolering**: Utbox-posten måste skrivas inom samma transaktion som Course-sparningen. Om
  `CourseService.save()` kastas, rollas både Course och utbox tillbaka.
- **DLQ-ackumulering**: DLQ kan växa om många meddelanden misslyckas. Övervakning rekommenderas.
- **mqreciver databas**: mqreciver behöver en ny databaskoppling. För utveckling kan H2 in-memory användas; för
  produktion rekommenderas PostgreSQL.
