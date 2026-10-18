package com.supermarket.pos.categories.repository;

import com.supermarket.pos.categories.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("select c from Category c left join fetch c.parent order by c.sortOrder, c.name")
    List<Category> findAllWithParent();

    boolean existsByCodeIgnoreCase(String code);

    @Query("select count(c) from Category c where c.parent.id = :id and c.active = true")
    long countActiveChildren(@Param("id") Long id);
}
