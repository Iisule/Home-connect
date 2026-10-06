package ng.proptech;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point for the NaijaProptech core API. */
@SpringBootApplication
@ConfigurationPropertiesScan // picks up ng.proptech.config.AppProperties
public class ProptechApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProptechApplication.class, args);
    }
}
