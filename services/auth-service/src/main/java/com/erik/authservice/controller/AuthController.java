package com.erik.authservice.controller;

import com.erik.authservice.dto.LoginRequest;
import com.erik.authservice.dto.RegistrationRequest;
import com.erik.authservice.exception.InvalidEmailOrPasswordException;
import com.erik.authservice.exception.UserAlreadyExistsException;
import com.erik.authservice.model.User;
import com.erik.authservice.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegistrationRequest request) {
        Map<String, String> conflictErrors = new HashMap<>();

        if (userRepository.existsByUsername(request.getUsername())) {
            conflictErrors.put("username", "This username is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            conflictErrors.put("email", "This email is already registered");
        }

        if (!conflictErrors.isEmpty()) {
            throw new UserAlreadyExistsException(conflictErrors);
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public String login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(
                () -> new InvalidEmailOrPasswordException("Invalid email or password")
        );
        boolean isMatch = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if(!isMatch) {
            throw new InvalidEmailOrPasswordException("Invalid email or password");
        }
        return "Login Successful";
    }
}