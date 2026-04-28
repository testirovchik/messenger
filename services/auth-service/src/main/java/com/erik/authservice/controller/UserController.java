package com.erik.authservice.controller;

import com.erik.authservice.dto.UserPublicDTO;
import com.erik.authservice.model.User;
import com.erik.authservice.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/search")
    public List<UserPublicDTO> searchUsers(@RequestParam String query) {
        return userRepository.searchByUserName(query)
                .stream()
                .map(user -> new UserPublicDTO(user.getId(), user.getUsername()))
                .toList();
    }
    @GetMapping("/getUsersByIds")
    ResponseEntity<List<User>> getUsersByIds(@Valid @RequestBody List<Long> userIds) {
        return ResponseEntity.ok(userRepository.findAllById(userIds));
    }
}