package dev.relayforge.events;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@EnableScheduling
@SpringBootApplication
public class EventsApplication {
    public static void main(String[] args) { SpringApplication.run(EventsApplication.class, args); }
}
