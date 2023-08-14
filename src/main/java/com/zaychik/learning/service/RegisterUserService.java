package com.zaychik.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.zaychik.learning.model.UserDto;
import com.zaychik.learning.model.auth.AuthenticationRequest;
import com.zaychik.learning.model.auth.AuthenticationResponce;
import com.zaychik.learning.model.auth.RegisterRequest;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class RegisterUserService {
    @Value("${rest.request.url}")
    private String restRequestUrl;

    @Autowired
    RestTemplate restTemplate;
    @Autowired
    protected ObjectMapper mapper;

    private static final String JOB_VAR_TOKEN = "token";
    private static final String JOB_VAR_EMAIL = "email";
    /**
     * Метод для блока Authentication.
     * Метод получает через параметры логин (authuser) и пароль для авторизации (authuserpassword)
     * Авторизируется в системе и записывает токен в параметры (JOB_VAR_TOKEN)
     * @param client - JobClient
     * @param job - ActivatedJob
     * @throws  JsonProcessingException из-за преобразования обекта в  json
     */
    @JobWorker(type = "authUser" )
    public void authUser(final JobClient client, final ActivatedJob job) throws JsonProcessingException {
        logJob(job);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (job.getVariablesAsMap().get("authuser") == null || job.getVariablesAsMap().get("authuserpassword") == null ){
            client.newFailCommand(job.getKey()).retries(0).send();
        }

        AuthenticationRequest user = AuthenticationRequest.builder()
                .email(job.getVariablesAsMap().get("authuser").toString())
                .password(job.getVariablesAsMap().get("authuserpassword").toString())
                .build();
        HttpEntity<String> request = new HttpEntity<>(mapper.writeValueAsString(user), headers);

        final AuthenticationResponce result = restTemplate.postForObject(
                restRequestUrl + "/api/v1/auth/authentication",
                request,
                AuthenticationResponce.class);
        client.newCompleteCommand(job.getKey())
                .variables(mapper.writeValueAsString(result))
                .send()
                .join();


    }
    /**
     * Метод для блока Get User.
     * Метод запрашивает пользователя по почте JOB_VAR_EMAIL.
     * На основе его наличии в системе вставляет новый параметр isUserExist
     * @param client - JobClient
     * @param job - ActivatedJob
     * @throws  URISyntaxException
     */
    @JobWorker(type = "getUser")
    public void getUser(final JobClient client, final ActivatedJob job) throws URISyntaxException {
        logJob(job);
        if (job.getVariablesAsMap().get(JOB_VAR_TOKEN) == null){
            client.newFailCommand(job.getKey()).retries(0).send();
        }
        if (job.getVariablesAsMap().get(JOB_VAR_EMAIL) == null){
            client.newFailCommand(job.getKey()).retries(0).send();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(job.getVariablesAsMap().get(JOB_VAR_TOKEN).toString());
        Map<String, String> variables = new HashMap<>();
        try {
            restTemplate.exchange(
                            RequestEntity.get(new URI(restRequestUrl + "/users/email?email=" + job.getVariablesAsMap().get(JOB_VAR_EMAIL).toString()))
                                    .headers(headers)
                                    .build(), UserDto.class)
                    .getBody();
            variables.put("isUserExist", "true");
        } catch (HttpClientErrorException.NotFound e){
            variables.put("isUserExist", "false");
        }
        client.newCompleteCommand(job.getKey())
                .variables(variables)
                .send()
                .join();
    }
    /**
     * Метод для блока Update user.
     * На основе параметра isUserExist, было принято решение, что пользователь существует!
     * Обновляем пользотвателя на ту информацию, которая передана в параметрах.
     * @param client - JobClient
     * @param job - ActivatedJob
     * @throws  URISyntaxException
     */
    @JobWorker(type = "updateUser")
    public void updateUser(final JobClient client, final ActivatedJob job) throws URISyntaxException {
        logJob(job);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(job.getVariablesAsMap().get(JOB_VAR_TOKEN).toString());

        Gson gson = new Gson();
        UserDto user = gson.fromJson(job.getVariablesAsMap().toString(), UserDto.class);

        restTemplate.exchange(
                        RequestEntity.put(new URI(restRequestUrl + "/users/email?email=" + job.getVariablesAsMap().get(JOB_VAR_EMAIL).toString()))
                                .headers(headers)
                                .body(user),
                        UserDto.class)
                .getBody();

        client.newCompleteCommand(job.getKey())
                .send()
                .join();
    }

    /**
     * Метод для блока Update user.
     * На основе параметра isUserExist, было принято решение, что пользователь НЕ существует!
     * Регистрируем пользователя с той информацией, которая передана в параметрах
     * @param client - JobClient
     * @param job - ActivatedJob
     * @throws  URISyntaxException
     */
    @JobWorker(type = "registerUser")
    public void registerUser(final JobClient client, final ActivatedJob job) throws URISyntaxException {
        logJob(job);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(job.getVariablesAsMap().get(JOB_VAR_TOKEN).toString());

        Gson gson = new Gson();
        RegisterRequest user = gson.fromJson(job.getVariablesAsMap().toString(), RegisterRequest.class);

        restTemplate.exchange(
                        RequestEntity.post(new URI(restRequestUrl + "/api/v1/auth/register"))
                                .headers(headers)
                                .body(user),
                        UserDto.class)
                .getBody();

        client.newCompleteCommand(job.getKey())
                .send()
                .join();
    }
    /**
     * Метод для вывода всей информации о работе, которая сейчас выполняется.
     * И с какими параметрами она емеет на вход.
     * @param job - ActivatedJob
     */
    private static void logJob(final ActivatedJob job) {
        log.info(
                "complete job\n>>> [type: {}, key: {}, element: {}, workflow instance: {}]\n{deadline; {}]\n[headers: {}]\n[variables: {}]",
                job.getType(),
                job.getKey(),
                job.getElementId(),
                job.getProcessInstanceKey(),
                Instant.ofEpochMilli(job.getDeadline()),
                job.getCustomHeaders(),
                job.getVariables());
    }
}
