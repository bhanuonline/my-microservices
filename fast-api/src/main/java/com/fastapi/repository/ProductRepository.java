package com.fastapi.repository;

import com.fastapi.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // ✅ Only select columns you need — never SELECT * on hot paths
    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.active = true ORDER BY p.id")
    List<Product> findActiveByCategory(@Param("category") String category);

    // ✅ Use exists instead of findById when you only need to check presence
    boolean existsByIdAndActive(Long id, Boolean active);

}
