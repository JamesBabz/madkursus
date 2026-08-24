package dk.jamesbabz.madkursus.inbound.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.jamesbabz.madkursus.MadkursusApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class PersistentSessionIntegrationTest {
    private static final ObjectMapper JSON=new ObjectMapper();

    @Test void jdbcSessionSurvivesRestartExpiresAndLogoutDeletesIt()throws Exception {
        try(EmbeddedPostgres postgres=EmbeddedPostgres.start()){
            var dataSource=postgres.getPostgresDatabase();Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();UUID userId=UUID.randomUUID();
            try(Connection connection=dataSource.getConnection();var insert=connection.prepareStatement("INSERT INTO users(id,username,password_hash,created_at,enabled) VALUES (?,?,?,CURRENT_TIMESTAMP,true)")){insert.setObject(1,userId);insert.setString(2,"persistent-user");insert.setString(3,new BCryptPasswordEncoder().encode("correct-password"));insert.executeUpdate();}
            CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);HttpClient browser=client(cookies);String url=postgres.getJdbcUrl("postgres","postgres");
            try(ConfigurableApplicationContext first=start(url)){URI base=base(first);HttpResponse<String> login=login(browser,base);assertThat(login.statusCode()).isEqualTo(200);assertThat(login.headers().allValues("set-cookie")).anySatisfy(value->assertThat(value).contains("SESSION=","Max-Age=2592000","HttpOnly","SameSite=Strict").doesNotContain("Secure"));assertThat(get(browser,base,"/v1/auth/me").statusCode()).isEqualTo(200);}
            try(Connection connection=dataSource.getConnection();var row=connection.createStatement().executeQuery("SELECT COUNT(*),MIN(max_inactive_interval) FROM spring_session")){assertThat(row.next()).isTrue();assertThat(row.getInt(1)).isEqualTo(1);assertThat(row.getInt(2)).isEqualTo(2_592_000);}
            try(ConfigurableApplicationContext second=start(url)){URI base=base(second);assertThat(get(browser,base,"/v1/auth/me").statusCode()).isEqualTo(200);
                String survivingSessionId;try(Connection connection=dataSource.getConnection();var row=connection.createStatement().executeQuery("SELECT session_id FROM spring_session WHERE principal_name='persistent-user'")){assertThat(row.next()).isTrue();survivingSessionId=row.getString(1);}
                CookieManager expiringCookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);HttpClient expiringBrowser=client(expiringCookies);assertThat(login(expiringBrowser,base).statusCode()).isEqualTo(200);try(Connection connection=dataSource.getConnection();var update=connection.prepareStatement("UPDATE spring_session SET last_access_time=0,expiry_time=0 WHERE principal_name='persistent-user' AND session_id<>?")){update.setString(1,survivingSessionId);assertThat(update.executeUpdate()).isEqualTo(1);}assertThat(get(expiringBrowser,base,"/v1/auth/me").statusCode()).isEqualTo(401);
                HttpClient invalid=HttpClient.newHttpClient();HttpRequest invalidRequest=HttpRequest.newBuilder(base.resolve("/v1/auth/me")).header("Cookie","SESSION=invalid").GET().build();assertThat(invalid.send(invalidRequest,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
                String csrf=csrf(browser,base);HttpRequest logoutRequest=HttpRequest.newBuilder(base.resolve("/v1/auth/logout")).header("Content-Type","application/json").header("X-XSRF-TOKEN",csrf).POST(HttpRequest.BodyPublishers.noBody()).build();HttpResponse<String> logout=browser.send(logoutRequest,HttpResponse.BodyHandlers.ofString());assertThat(logout.statusCode()).isEqualTo(204);assertThat(logout.headers().allValues("set-cookie")).anySatisfy(value->assertThat(value).contains("SESSION=","Max-Age=0"));assertThat(get(browser,base,"/v1/auth/me").statusCode()).isEqualTo(401);}
            try(ConfigurableApplicationContext third=start(url)){assertThat(get(browser,base(third),"/v1/auth/me").statusCode()).isEqualTo(401);}
            try(Connection connection=dataSource.getConnection();var row=connection.createStatement().executeQuery("SELECT COUNT(*) FROM spring_session WHERE principal_name='persistent-user' AND expiry_time>0")){assertThat(row.next()).isTrue();assertThat(row.getInt(1)).isZero();}
        }
    }

    private ConfigurableApplicationContext start(String url){return new SpringApplicationBuilder(MadkursusApplication.class).web(WebApplicationType.SERVLET).run("--server.port=0","--spring.datasource.url="+url,"--spring.datasource.username=postgres","--spring.datasource.password=","--spring.jpa.hibernate.ddl-auto=validate","--server.servlet.session.cookie.secure=false");}
    private URI base(ConfigurableApplicationContext context){int port=((ServletWebServerApplicationContext)context).getWebServer().getPort();return URI.create("http://localhost:"+port);}
    private HttpClient client(CookieManager cookies){return HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();}
    private HttpResponse<String> login(HttpClient client,URI base)throws Exception{String csrf=csrf(client,base);String body="{\"username\":\"persistent-user\",\"password\":\"correct-password\"}";HttpRequest request=HttpRequest.newBuilder(base.resolve("/v1/auth/login")).header("Content-Type","application/json").header("X-XSRF-TOKEN",csrf).POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();return client.send(request,HttpResponse.BodyHandlers.ofString());}
    private String csrf(HttpClient client,URI base)throws Exception{HttpResponse<String> response=get(client,base,"/v1/auth/csrf");assertThat(response.statusCode()).isEqualTo(200);return JSON.readTree(response.body()).path("token").asText();}
    private HttpResponse<String> get(HttpClient client,URI base,String path)throws Exception{return client.send(HttpRequest.newBuilder(base.resolve(path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
}
