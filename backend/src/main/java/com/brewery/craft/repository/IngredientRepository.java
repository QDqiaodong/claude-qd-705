package com.brewery.craft.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.brewery.craft.domain.Ingredient;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    List<Ingredient> findAllByOrderByCodeAsc();

    Optional<Ingredient> findByCode(String code);

    boolean existsByCode(String code);

    List<Ingredient> findAllByTypeOrderByCodeAsc(String type);

    long countByType(String type);
}
