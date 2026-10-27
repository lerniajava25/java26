package org.example.spring;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface CourseRepository extends ListCrudRepository<Course, Long> {

    @EntityGraph(value = "Course.detail")
    @NonNull List<Course> findAllBy();

    List<Course> findByCreditsLessThan(int credits);

    @Query("select c from Course c where c.credits < :credits")
    List<Course> findByCreditsLessThanCustom(int credits);
}
