package com.ryanpurakal.pariscompass;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.ryanpurakal.pariscompass.support.TestcontainersConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ParisCompassApplicationTests {

	@Test
	void contextLoads() {
	}

}
