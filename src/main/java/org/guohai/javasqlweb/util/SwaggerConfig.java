package org.guohai.javasqlweb.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * @author guohai
 */
@Configuration
@EnableWebMvc
public class SwaggerConfig {
    @Value("${spring.profiles.active:NA}")
    private String active;

    @Bean
    public Docket createRestApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()

                .apis(RequestHandlerSelectors.basePackage("org.guohai.javasqlweb.controller"))
                .paths(PathSelectors.any())
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("JavaSqlWeb 接口文档")
                .description("接口文档")
                .contact(new Contact("a","",""))
                .version("1.0")
                .build();
    }

}
