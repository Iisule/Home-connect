package ng.proptech.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import ng.proptech.service.FileStorageService;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Static media hosting and the HTTP client used to reach the Python AI service. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FileStorageService storage;

    public WebConfig(FileStorageService storage) {
        this.storage = storage;
    }

    /** Serves uploaded property photos at /media/**. Only the PUBLIC directory is mapped. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**")
                .addResourceLocations(storage.publicDir().toUri().toString())
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic());
    }

    /** RestTemplate with tight timeouts so a slow AI service can never hang an upload request. */
    @Bean
    public RestTemplate aiRestTemplate(RestTemplateBuilder builder, AppProperties props) {
        return builder
                .setConnectTimeout(Duration.ofMillis(props.ai().connectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(props.ai().readTimeoutMs()))
                .build();
    }
}
