package org.example.mqreciver.repository;

import org.example.mqreciver.entity.Inbox;
import org.springframework.data.repository.ListCrudRepository;

import java.util.UUID;

public interface InboxRepository extends ListCrudRepository<Inbox, UUID> {
}
