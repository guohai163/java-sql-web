package org.guohai.javasqladmin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类
 * @author guohai
 */
@SpringBootApplication
@MapperScan("org.guohai.javasqladmin.dao")
public class JavasqladminApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavasqladminApplication.class, args);
    }

}
