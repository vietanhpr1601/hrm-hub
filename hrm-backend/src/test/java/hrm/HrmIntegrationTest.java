package hrm;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Uses an isolated in-memory database unless HRM_TEST_DB_* explicitly selects a
 * disposable regression database. Regular SPRING_DATASOURCE_* settings cannot
 * accidentally point tests at the application database.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(properties = {
        "app.mail.enabled=false",
        "spring.datasource.url=${HRM_TEST_DB_URL:jdbc:h2:mem:hrm-test;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR,MONTH}",
        "spring.datasource.username=${HRM_TEST_DB_USERNAME:sa}",
        "spring.datasource.password=${HRM_TEST_DB_PASSWORD:}",
        "spring.datasource.driver-class-name=${HRM_TEST_DB_DRIVER:org.h2.Driver}",
        "spring.jpa.properties.hibernate.dialect=${HRM_TEST_DB_DIALECT:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "debug=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public @interface HrmIntegrationTest {
}
