package com.gms.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Boot entry point. {@link EnableAsync} powers
 * {@link com.gms.gateway.service.AppointmentConfirmationService#notifyConfirmation}
 * so booking redirects don't block on confirmation logging.
 */
@SpringBootApplication
@EnableAsync
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }

}
