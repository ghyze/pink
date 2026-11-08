package nl.pinkroccade.threekid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class ThreekidApplication {

	public static void main(String[] args) {
		SpringApplication.run(ThreekidApplication.class, args);
	}

	/** The clock that decides what "today" is; tests replace it with a fixed clock. */
	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

}
