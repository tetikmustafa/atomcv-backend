package com.mustafatetik.atomcv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AtomCvApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtomCvApplication.class, args);
    }
}
