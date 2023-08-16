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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final ObjectMapper mapper;
    private final RestSystemClient restSystemClient;

    private static final String JOB_VAR_TOKEN = "token";
    private static final String JOB_VAR_EMAIL = "email";
    /**
     * Метод для блока Authentication.
     * Метод получает через параметры логин (authuser) и пароль для авторизации (authuserpassword)
     * Авторизоваться в системе и записывает токен в параметры (JOB_VAR_TOKEN)
     * @param client - JobClient
     * @param job - ActivatedJob
     * @throws  JsonProcessingException из-за преобразования объекта в json
     */
    @JobWorker(type = "authUser" )
    public void authUser(final JobClient client, final ActivatedJob job) throws JsonProcessingException {
        logJob(job);

        if (job.getVariablesAsMap().get("authuser") == null || job.getVariablesAsMap().get("authuserpassword") == null ){
            client.newFailCommand(job.getKey()).retries(0).send();
        }

        AuthenticationRequest user = AuthenticationRequest.builder()
                .email(job.getVariablesAsMap().get("authuser").toString())
                .password(job.getVariablesAsMap().get("authuserpassword").toString())
                .build();

        final AuthenticationResponce result = restSystemClient.postUserAuth(user);

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
     */
    @JobWorker(type = "getUser")
    public void getUser(final JobClient client, final ActivatedJob job){
        logJob(job);
        if (job.getVariablesAsMap().get(JOB_VAR_TOKEN) == null){
            client.newFailCommand(job.getKey()).retries(0).send();
        }
        if (job.getVariablesAsMap().get(JOB_VAR_EMAIL) == null){
            client.newFailCommand(job.getKey()).retries(0).send();
        }

        Map<String, String> variables = new HashMap<>();
        try {
            restSystemClient.getUserByEmail(
                    job.getVariablesAsMap().get(JOB_VAR_TOKEN).toString(),
                    job.getVariablesAsMap().get(JOB_VAR_EMAIL).toString());

            variables.put("isUserExist", "true");
        } catch (WebClientResponseException.NotFound e){
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
     * Обновляем пользователя на ту информацию, которая передана в параметрах.
     * @param client - JobClient
     * @param job - ActivatedJob
     */
    @JobWorker(type = "updateUser")
    public void updateUser(final JobClient client, final ActivatedJob job){
        logJob(job);

        Gson gson = new Gson();
        UserDto user = gson.fromJson(job.getVariablesAsMap().toString(), UserDto.class);

        restSystemClient.putUserByEmail(
                job.getVariablesAsMap().get(JOB_VAR_TOKEN).toString(),
                job.getVariablesAsMap().get(JOB_VAR_EMAIL).toString(),
                user
        );

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
     */
    @JobWorker(type = "registerUser")
    public void registerUser(final JobClient client, final ActivatedJob job){
        logJob(job);

        Gson gson = new Gson();
        RegisterRequest user = gson.fromJson(job.getVariablesAsMap().toString(), RegisterRequest.class);

        restSystemClient.postUserRegister(user);

        client.newCompleteCommand(job.getKey())
                .send()
                .join();
    }
    /**
     * Метод для вывода всей информации о работе, которая сейчас выполняется.
     * И с какими параметрами она имеет на вход.
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
