package com.zaychik.learning.service;

import com.zaychik.learning.model.UserDto;
import com.zaychik.learning.model.auth.AuthenticationRequest;
import com.zaychik.learning.model.auth.AuthenticationResponce;
import com.zaychik.learning.model.auth.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RestSystemClient {

    private final WebClient webClient;
    public UserDto getUserByEmail(String token, String email){
        return webClient
                .get()
                .uri(String.join("", "/users/email?email=", email))
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .bodyToMono(UserDto.class)
                .block();
    }
    public AuthenticationResponce postUserAuth(AuthenticationRequest user){
        return webClient
                .post()
                .uri("/api/v1/auth/authentication")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(Mono.just(user), AuthenticationRequest.class)
                .retrieve()
                .bodyToMono(AuthenticationResponce.class)
                .block();
    }

    public UserDto putUserByEmail(String token, String email, UserDto user){
        return webClient
                .put()
                .uri(String.join("", "/users/email?email=", email))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .headers(h -> h.setBearerAuth(token))
                .body(Mono.just(user), UserDto.class)
                .retrieve()
                .bodyToMono(UserDto.class)
                .block();
    }

    public AuthenticationResponce postUserRegister(RegisterRequest user){
        return webClient
                .post()
                .uri("/api/v1/auth/register")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(Mono.just(user), RegisterRequest.class)
                .retrieve()
                .bodyToMono(AuthenticationResponce.class)
                .block();

    }
}