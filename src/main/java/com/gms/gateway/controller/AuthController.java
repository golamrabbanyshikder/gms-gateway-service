package com.gms.gateway.controller;

import com.gms.gateway.config.JwtTokenProvider;
import com.gms.gateway.entity.User;
import com.gms.gateway.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        Optional<User> userOpt = authService.authenticate(loginRequest.getUsername(), loginRequest.getPassword());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());
            return ResponseEntity.ok(new LoginResponse(token, "Login successful", user.getRole().name()));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new LoginResponse(null, "Invalid credentials", null));
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String token) {
        if (token != null && token.startsWith("Bearer ")) {
            String tokenValue = token.substring(7);
            if (jwtTokenProvider.validateToken(tokenValue)) {
                return ResponseEntity.ok(new TokenValidationResponse(true, "Token is valid"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new TokenValidationResponse(false, "Invalid token"));
    }

    public static class LoginRequest {
        public String username;
        public String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class LoginResponse {
        public String token;
        public String message;
        public String role;

        public LoginResponse(String token, String message, String role) {
            this.token = token;
            this.message = message;
            this.role = role;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    public static class TokenValidationResponse {
        public boolean valid;
        public String message;

        public TokenValidationResponse(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
