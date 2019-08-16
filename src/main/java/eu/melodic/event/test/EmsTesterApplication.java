package eu.melodic.event.test;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class EmsTesterApplication implements CommandLineRunner {

	@Autowired
	private Coordinator coordinator;

	public static void main(String[] args) {
		SpringApplication.run(EmsTesterApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		coordinator.startProcess();
	}
}
