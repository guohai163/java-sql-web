package org.guohai.javasqlweb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import springfox.documentation.oas.annotations.EnableOpenApi;

/**
 * 启动类
 * @author guohai
 */

@EnableOpenApi
@SpringBootApplication
@MapperScan("org.guohai.javasqlweb.dao")
@EnableWebMvc
public class JavasqlwebApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavasqlwebApplication.class, args);
    }

}
