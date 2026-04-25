package com.erik.authservice.controller;

import com.erik.authservice.dto.UserPublicDTO;
import com.erik.authservice.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}