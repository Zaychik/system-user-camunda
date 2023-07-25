package com.zaychik.learning.service;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.*;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.assertions.DeploymentAssert;
import io.camunda.zeebe.process.test.assertions.ProcessInstanceAssert;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;
import io.camunda.zeebe.process.test.filters.RecordStream;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
    final private String NAME_BPMN = "new-bpmn-diagram.bpmn";
    Map<String, Object> variables;
    private ZeebeTestEngine engine;
    private ZeebeClient client;
    private RecordStream recordStream;

    private String initDeployment(String nameBpmn){
        DeploymentEvent event = client.newDeployCommand()
                .addResourceFromClasspath(nameBpmn)
                .send()
                .join();
        return event.getProcesses().get(0).getBpmnProcessId();
    }

    private ProcessInstanceAssert initProcessInstanceStart(String bpmnProcessId) {
        ProcessInstanceEvent event = client.newCreateInstanceCommand()
                .bpmnProcessId(bpmnProcessId)
                .latestVersion()
                .send()
                .join();
        return BpmnAssert.assertThat(event);
    }

    @Test
    public void testDeployment() {
        //When
        DeploymentEvent event = client.newDeployCommand()
                .addResourceFromClasspath(NAME_BPMN)
                .send()
                .join();

        //Then
        BpmnAssert.assertThat(event);
    }

    @Test
    public void testProcessInstanceStart(){
        //Given
        String bpmnProcessId = initDeployment(NAME_BPMN);

        //When
        ProcessInstanceEvent event = client.newCreateInstanceCommand()
                .bpmnProcessId(bpmnProcessId)
                .latestVersion()
                .send()
                .join();

        //Then
        ProcessInstanceAssert assertions = BpmnAssert.assertThat(event);
        assertions.hasPassedElement("StartEvent");
    }


    @Test
    public void testJobAssertion() throws Exception {
        //Given
        String bpmnProcessId = initDeployment(NAME_BPMN);
        initProcessInstanceStart(bpmnProcessId);
        //When
        ActivateJobsResponse response = client.newActivateJobsCommand()
                .jobType("authUser")
                .maxJobsToActivate(1)
                .send()
                .join();

        ActivatedJob activatedJob = getActivatedJob(response);
        //Then

        BpmnAssert.assertThat(activatedJob);
        client.newCompleteCommand(activatedJob.getKey()).send().join();
    }

    private ActivatedJob getActivatedJob(ActivateJobsResponse response) throws Exception {
        int duration = 1000;
        while(response.getJobs().size()<1) {
            Thread.sleep(duration);
            duration+=1000;
            if(duration == 10000)
                throw new Exception("Job waiting period exceeded");
        }
        return response.getJobs().get(0);
    }
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

        /*ProcessInstanceResult event2 = client.newCreateInstanceCommand()
                .bpmnProcessId(event.getProcesses().get(0).getBpmnProcessId())
                .latestVersion()
                .withResult()
                .send()
                .join();*/

        //ProcessInstanceAssert assertions2 = BpmnAssert.assertThat(processInstance);
        waitForProcessInstanceCompleted(processInstance);
        //waitForUserTaskAndComplete("auth", Collections.singletonMap("approved", true));

        /*BpmnAssert.assertThat(processInstance)
                .hasPassedElement("auth")
                .isCompleted();*/

    }
}
