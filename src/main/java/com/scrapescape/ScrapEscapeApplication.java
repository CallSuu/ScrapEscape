package com.scrapescape;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.util.Random;
import java.util.random.RandomGenerator;
@SpringBootApplication
public class ScrapEscapeApplication {
    @Bean RandomGenerator gameRandom() { return new Random(); }
    public static void main(String[] args) { SpringApplication.run(ScrapEscapeApplication.class, args); }
}
