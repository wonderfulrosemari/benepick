package com.benepick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BenepickApplication {

    public static void main(String[] args) {
        SpringApplication.run(BenepickApplication.class, args);
    }
}
