package org.example.springwithdatabase.repository;

import org.example.springwithdatabase.entity.Djur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DjurRepository extends JpaRepository<Djur, Long> {
}
