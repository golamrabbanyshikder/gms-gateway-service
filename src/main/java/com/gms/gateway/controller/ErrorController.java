package com.gms.gateway.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {

    @GetMapping("/error/403")
    public String error403() {
        return "error/403";
    }

    @GetMapping("/error/404")
    public String error404() {
        return "error/404";
    }

    @GetMapping("/error/500")
    public String error500() {
        return "error/500";
    }

    // Spring Security forwards access-denied (403) and the servlet container
    // forwards 404s here via server.error.path - this must branch on the real
    // status, otherwise every error (e.g. a DOCTOR hitting an admin-only page)
    // renders as a misleading "500 Server Error".
    @GetMapping("/error")
    public String handleError(HttpServletRequest request, HttpServletResponse response) {
        Integer status = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = status != null ? status : 500;
        response.setStatus(code);
        if (code == 403) {
            return "error/403";
        }
        if (code == 404) {
            return "error/404";
        }
        return "error/500";
    }
}
