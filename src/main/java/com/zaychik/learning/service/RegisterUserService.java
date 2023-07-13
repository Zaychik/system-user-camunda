package com.zaychik.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaychik.learning.model.UserDto;
import com.zaychik.learning.model.auth.AuthenticationRequest;
import com.zaychik.learning.model.auth.AuthenticationResponce;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@EnableZeebeClient
public class RegisterUserService {
    @Value("${rest.request.url}")
    private String restRequestUrl;

    @Autowired
    RestTemplate restTemplate;
    @Autowired
    protected ObjectMapper mapper;

    @ZeebeWorker(type = "authUser" )
    public void authUser(final JobClient client, final ActivatedJob job) throws JsonProcessingException {
        logJob(job);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (job.getVariablesAsMap().get("authuser") == null | job.getVariablesAsMap().get("authuserpassword") == null ){
            client.newFailCommand(job.getKey()).retries(0).send();
        }

        AuthenticationRequest user = AuthenticationRequest.builder()
                .email(job.getVariablesAsMap().get("authuser").toString())
                .password(job.getVariablesAsMap().get("authuserpassword").toString())
                .build();
        HttpEntity<String> request = new HttpEntity<String>(mapper.writeValueAsString(user), headers);

        final AuthenticationResponce result = restTemplate.postForObject(
                restRequestUrl + "/api/v1/auth/authentication",
                request,
                AuthenticationResponce.class);
        client.newCompleteCommand(job.getKey())
                .variables(mapper.writeValueAsString(result))
                .send()
                .join();


    }

    @ZeebeWorker(type = "getUser")
    public void getUser(final JobClient client, final ActivatedJob job) throws URISyntaxException {
        logJob(job);
        if (job.getVariablesAsMap().get("token") == null){
            client.newFailCommand(job.getKey()).retries(0).send();
        }
        if (job.getVariablesAsMap().get("id") == null){
            client.newFailCommand(job.getKey()).retries(0).send();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(job.getVariablesAsMap().get("token").toString());
        UserDto userDto = restTemplate.exchange(
                RequestEntity.get(new URI(restRequestUrl + "/users/" + job.getVariablesAsMap().get("id").toString()))
                        .headers(headers)
                        .build(), UserDto.class)
                .getBody();
        Map<String, String> variables  = new HashMap<String, String>(){{
            put("isUserExist", "true");
        }};

        client.newCompleteCommand(job.getKey())
                .variables(variables)
                .send()
                .join();
    }

    @ZeebeWorker(type = "updateUser")
    public void updateUser(final JobClient client, final ActivatedJob job)  {
        logJob(job);
        client.newCompleteCommand(job.getKey())
                .send()
                .join();
    }

    @ZeebeWorker(type = "registerUser")
    public void registerUser(final JobClient client, final ActivatedJob job)  {
        logJob(job);
        client.newCompleteCommand(job.getKey())
                .send()
                .join();
    }

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
