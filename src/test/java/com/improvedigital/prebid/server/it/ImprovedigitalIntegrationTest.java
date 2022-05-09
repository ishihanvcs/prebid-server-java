package com.improvedigital.prebid.server.it;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.mapping.Jackson2Mapper;
import io.restassured.specification.RequestSpecification;
import org.prebid.server.it.IntegrationTest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.test.context.TestPropertySource;

import static io.restassured.RestAssured.given;

@SuppressWarnings("checkstyle:hideutilityclassconstructor")
@SpringBootApplication(
        scanBasePackages = {
                "org.prebid.server",
                "com.improvedigital.prebid.server"
        }
)
@TestPropertySource(properties = {
        "settings.filesystem.stored-imps-dir=src/test/resources/com/improvedigital/prebid/server/it/storedimps",
        "admin.port=18060",
        "http.port=18080",
})
public class ImprovedigitalIntegrationTest extends IntegrationTest {

    protected static RequestSpecification specWithPBSHeader() {
        return given(spec())
                .header("Referer", "http://pbs.improvedigital.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/100.0.4896.60 Safari/537.36")
                .header("Origin", "http://pbs.improvedigital.com");
    }

    private static RequestSpecification spec() {
        return new RequestSpecBuilder()
                .setBaseUri("http://localhost")
                .setPort(18080)
                .setConfig(RestAssuredConfig.config()
                        .objectMapperConfig(new ObjectMapperConfig(new Jackson2Mapper((aClass, s) -> mapper))))
                .build();
    }
}
