package com.gms.gateway.controller;

import com.gms.gateway.config.JwtTokenProvider;
import com.gms.gateway.entity.Role;
import com.gms.gateway.entity.User;
import com.gms.gateway.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.Optional;

@Controller
public class AuthViewController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                             @RequestParam(required = false) String message,
                             Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid username or password");
        }
        if (message != null) {
            model.addAttribute("message", message);
        }
        return "auth/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @GetMapping("/verify-otp")
    public String verifyOtpPage() {
        return "auth/verify-otp";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "auth/reset-password";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password,
                       @RequestParam(required = false) boolean rememberMe,
                       HttpServletResponse response) {
        Optional<User> userOpt = authService.authenticate(username, password);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt_token", token);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            if (rememberMe) {
                cookie.setMaxAge(7 * 24 * 60 * 60);
            }
            response.addCookie(cookie);

            Role role = user.getRole();
            if (role == Role.SUPER_ADMIN || role == Role.HOSPITAL_ADMIN) {
                return "redirect:/admin-dashboard";
            } else if (role == Role.DOCTOR) {
                return "redirect:/doctor-dashboard";
            } else if (role == Role.PATIENT) {
                return "redirect:/patient/dashboard";
            }
            return "redirect:/dashboard";
        }
        return "redirect:/login?error";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, Model model) {
        model.addAttribute("message", "OTP sent to " + email);
        return "auth/forgot-password";
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam String otp) {
        return "redirect:/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String password, @RequestParam String confirmPassword) {
        if (password.equals(confirmPassword)) {
            return "redirect:/login?message=Password reset successfully";
        }
        return "redirect:/reset-password?error=Passwords do not match";
    }

    @GetMapping("/logout")
    public String logout(HttpServletResponse response) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt_token", "");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return "redirect:/login";
    }

    @GetMapping("/session-expired")
    public String sessionExpired() {
        return "auth/session-expired";
    }
}
