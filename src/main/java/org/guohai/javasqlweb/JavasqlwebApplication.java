package org.guohai.javasqlweb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类
 * @author guohai
 */
@SpringBootApplication
@MapperScan("org.guohai.javasqlweb.dao")
public class JavasqlwebApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavasqlwebApplication.class, args);
    }

}
