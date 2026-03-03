package com.purelearning.smart_meter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@MapperScan("com.purelearning.smart_meter.mapper")
@ConfigurationPropertiesScan("com.purelearning.smart_meter.config.props")
public class SmartMeterApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartMeterApplication.class, args);
    }

}
