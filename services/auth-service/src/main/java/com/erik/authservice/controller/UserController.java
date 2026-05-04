package com.erik.authservice.controller;

import com.erik.authservice.dto.UserPublicDTO;
import com.erik.authservice.model.User;
import com.erik.authservice.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Endpoints for searching and retrieving user profiles")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Operation(summary = "Search users by name", description = "Returns a list of users whose usernames start with the provided search query.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved matching users")
    @GetMapping("/search")
    public List<UserPublicDTO> searchUsers(
            @Parameter(description = "The username prefix to search for", example = "eri")
            @RequestParam String query) {
        return userRepository.searchByUserName(query)
                .stream()
                .map(user -> new UserPublicDTO(user.getId(), user.getUsername()))
                .toList();
    }

    @Operation(summary = "Get users by IDs", description = "Fetches a complete list of User entities based on a provided array of User IDs.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested users")
    @GetMapping("/getUsersByIds")
    ResponseEntity<List<User>> getUsersByIds(
            @Parameter(description = "A JSON array of user IDs", example = "[1, 2, 3]")
            @Valid @RequestBody List<Long> userIds) {
        return ResponseEntity.ok(userRepository.findAllById(userIds));
    }
}