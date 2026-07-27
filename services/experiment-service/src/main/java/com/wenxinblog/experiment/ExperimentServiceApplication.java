package com.wenxinblog.experiment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class ExperimentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExperimentServiceApplication.class, args);
    }
}
