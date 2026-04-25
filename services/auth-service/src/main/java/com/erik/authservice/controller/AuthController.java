package com.erik.authservice.controller;

import com.erik.authservice.dto.LoginRequest;
import com.erik.authservice.dto.LoginResponse;
import com.erik.authservice.dto.RegistrationRequest;
import com.erik.authservice.exception.InvalidEmailOrPasswordException;
import com.erik.authservice.exception.UserAlreadyExistsException;
import com.erik.authservice.model.User;
import com.erik.authservice.repository.UserRepository;
import com.erik.authservice.service.JwtService;
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
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(
                () -> new InvalidEmailOrPasswordException("Invalid email or password")
        );
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidEmailOrPasswordException("Invalid email or password");
        }
        String token = jwtService.generateToken(user.getId().toString());
        return new LoginResponse(token);
    }
}