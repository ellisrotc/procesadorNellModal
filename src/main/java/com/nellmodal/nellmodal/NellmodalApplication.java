package com.nellmodal.nellmodal;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class NellmodalApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(NellmodalApplication.class)
                .headless(false)
                .run(args);
    }

}
