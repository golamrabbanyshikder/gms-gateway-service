package com.gms.gateway.controller;

import com.gms.gateway.client.AuditClient;
import com.gms.gateway.entity.Role;
import com.gms.gateway.entity.User;
import com.gms.gateway.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/users")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN')")
public class UserViewController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditClient auditClient;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

    @GetMapping
    public String userList(Model model) {
        model.addAttribute("title", "Users");
        model.addAttribute("pageTitle", "User Management");
        model.addAttribute("username", currentUsername());
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("view", "user/list");
        return "layout";
    }

    @GetMapping("/create")
    public String createUser(Model model) {
        model.addAttribute("title", "Create User");
        model.addAttribute("pageTitle", "Create New User");
        model.addAttribute("username", currentUsername());
        model.addAttribute("roles", Role.values());
        model.addAttribute("view", "user/create");
        return "layout";
    }

    @PostMapping("/create")
    public String submitCreateUser(@RequestParam String username,
                                    @RequestParam String fullName,
                                    @RequestParam(required = false) String email,
                                    @RequestParam String password,
                                    @RequestParam Role role,
                                    @RequestParam(required = false) Long hospitalId,
                                    HttpServletRequest request,
                                    Model model) {
        if (userRepository.findByUsername(username).isPresent()) {
            model.addAttribute("title", "Create User");
            model.addAttribute("pageTitle", "Create New User");
            model.addAttribute("username", currentUsername());
            model.addAttribute("roles", Role.values());
            model.addAttribute("error", "Username already exists.");
            model.addAttribute("view", "user/create");
            return "layout";
        }

        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setHospitalId(hospitalId);
        user.setEnabled(true);
        User saved = userRepository.save(user);

        auditClient.log(saved.getUserId(), currentUsername(), "USER_CREATED", "User", saved.getUserId(),
                "Created user " + username + " with role " + role, request.getRemoteAddr());
        return "redirect:/users";
    }

    @GetMapping("/edit/{id}")
    public String editUser(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Edit User");
        model.addAttribute("pageTitle", "Edit User");
        model.addAttribute("username", currentUsername());
        model.addAttribute("userId", id);
        model.addAttribute("roles", Role.values());
        userRepository.findById(id).ifPresent(u -> model.addAttribute("user", u));
        model.addAttribute("view", "user/edit");
        return "layout";
    }

    @PostMapping("/edit/{id}")
    public String submitEditUser(@PathVariable Long id,
                                  @RequestParam String fullName,
                                  @RequestParam(required = false) String email,
                                  @RequestParam Role role,
                                  @RequestParam(required = false) Boolean enabled,
                                  HttpServletRequest request) {
        var existing = userRepository.findById(id);
        if (existing.isEmpty()) {
            return "redirect:/users";
        }
        User user = existing.get();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setEnabled(enabled != null && enabled);
        userRepository.save(user);

        auditClient.log(id, currentUsername(), "USER_UPDATED", "User", id,
                "Updated user " + user.getUsername(), request.getRemoteAddr());
        return "redirect:/users";
    }

    @GetMapping("/view/{id}")
    public String viewUser(@PathVariable Long id, Model model) {
        model.addAttribute("title", "User Details");
        model.addAttribute("pageTitle", "User Details");
        model.addAttribute("username", currentUsername());
        model.addAttribute("userId", id);
        userRepository.findById(id).ifPresent(u -> model.addAttribute("user", u));
        model.addAttribute("view", "user/view");
        return "layout";
    }

    @GetMapping("/roles")
    public String assignRoles(Model model) {
        model.addAttribute("title", "Assign Roles");
        model.addAttribute("pageTitle", "Assign User Roles");
        model.addAttribute("username", currentUsername());
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("roles", Role.values());
        model.addAttribute("view", "user/roles");
        return "layout";
    }

    @GetMapping("/activity")
    public String userActivity(Model model) {
        model.addAttribute("title", "User Activity");
        model.addAttribute("pageTitle", "User Activity");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "user/activity");
        return "layout";
    }
}
