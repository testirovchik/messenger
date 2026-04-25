package com.erik.authservice.repository;

import com.erik.authservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    @Query(value = "SELECT * FROM users u WHERE u.username ILIKE CONCAT(:searchTerm, '%')", nativeQuery = true)
    List<User> searchByUserName(@Param("searchTerm") String searchTerm);
}