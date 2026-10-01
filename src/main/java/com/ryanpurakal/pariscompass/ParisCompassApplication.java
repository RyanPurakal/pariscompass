package com.ryanpurakal.pariscompass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ParisCompassApplication {

	public static void main(String[] args) {
		SpringApplication.run(ParisCompassApplication.class, args);
	}

}
