package org.example.spring.repository;

import org.example.spring.entity.Outbox;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends ListCrudRepository<Outbox, UUID> {

    List<Outbox> findAllByPublishedFalse();
}
