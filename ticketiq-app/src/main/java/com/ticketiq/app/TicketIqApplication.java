package com.ticketiq.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TicketIQ: an AI-enhanced helpdesk.
 *
 * <p>Version 1 was a plain layered ticketing app (create, triage by hand, resolve). Version 2 keeps that
 * app intact and adds an AI layer that triages tickets, spots duplicates and drafts replies. The AI code
 * lives in the {@code ticketiq-core} module and is reached only through the {@code ai} package here,
 * so tickets keep working when the AI is slow, wrong or switched off.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
@EnableCaching
public class TicketIqApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketIqApplication.class, args);
    }
}
