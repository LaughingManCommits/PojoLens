package laughing.man.commits.examples.spring.boot.riskconsole;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RiskConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskConsoleApplication.class, args);
    }
}
