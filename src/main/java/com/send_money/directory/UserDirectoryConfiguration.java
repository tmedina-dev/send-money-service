package com.send_money.directory;

import com.send_money.common.DownstreamLoggingInterceptor;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class UserDirectoryConfiguration {

    @Bean
    RestClient userDirectoryRestClient(@Value("${user-directory.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .requestInterceptor(new DownstreamLoggingInterceptor()).build();
    }
}
