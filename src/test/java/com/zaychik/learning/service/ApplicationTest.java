package com.zaychik.learning.service;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.assertions.DeploymentAssert;
import io.camunda.zeebe.process.test.assertions.ProcessInstanceAssert;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;
import io.camunda.zeebe.process.test.filters.RecordStream;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.HashMap;
import java.util.Map;

import static io.camunda.zeebe.spring.test.ZeebeTestThreadSupport.waitForProcessInstanceCompleted;


@ZeebeProcessTest
//@SpringBootTest
//@ZeebeSpringTest
public class ApplicationTest {
    @MockBean
    private RegisterUserService service;
    Map<String, Object> variables;
    private ZeebeTestEngine engine;
    private ZeebeClient client;
    private RecordStream recordStream;
    @Test
    void testRegisterUserService(){
        variables = new HashMap<>();
        variables.put("name", "User7");
        variables.put("email", "user9@gmail.com");
        variables.put("phone", "89036874010");
        variables.put("role", "USER");
        variables.put("password", "1234");
        variables.put("authuser", "admin@gmail.com");
        variables.put("authuserpassword", "1234");

        DeploymentEvent event = client.newDeployResourceCommand()
                .addResourceFromClasspath("new-bpmn-diagram.bpmn")
                .send()
                .join();
        DeploymentAssert assertions = BpmnAssert.assertThat(event);

        ProcessInstanceEvent processInstance = client.newCreateInstanceCommand()
                .bpmnProcessId(event.getProcesses().get(0).getBpmnProcessId())
                .latestVersion()
                .send()
                .join();

        ProcessInstanceAssert assertions2 = BpmnAssert.assertThat(processInstance);
        //aitForProcessInstanceCompleted(processInstance);
        //waitForUserTaskAndComplete("auth", Collections.singletonMap("approved", true));

        BpmnAssert.assertThat(processInstance)
                .hasPassedElement("auth")
                .isCompleted();

    }
}
