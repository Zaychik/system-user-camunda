package com.zaychik.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.assertions.ProcessInstanceAssert;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;
import io.camunda.zeebe.process.test.filters.RecordStream;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.web.client.RestTemplate;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;


@ZeebeProcessTest
//@SpringBootTest
//@ZeebeSpringTest
public class ApplicationTest {
    final private String NAME_BPMN = "new-bpmn-diagram.bpmn";
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
    private void ActivateJobCompleteCommand(String jobType, Map<String, String> variables) throws Exception {
        ActivateJobsResponse response = client.newActivateJobsCommand()
                .jobType(jobType)
                .maxJobsToActivate(1)
                .send()
                .join();

        ActivatedJob activatedJob = getActivatedJob(response);
        if (variables != null && variables.size() > 0) {
            client.newCompleteCommand(activatedJob.getKey()).variables(variables).send().join();
        } else {
            client.newCompleteCommand(activatedJob.getKey()).send().join();
        }
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
        ProcessInstanceAssert instanceAssert = initProcessInstanceStart(bpmnProcessId);
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

    @Test
    public void testUpdateOfInstance() throws Exception {

        Map<String, String>  variables  = new HashMap<String, String>(){{
            put("isUserExist", "true");
        }};

        String bpmnProcessId = initDeployment(NAME_BPMN);
        ProcessInstanceAssert instanceAssert = initProcessInstanceStart(bpmnProcessId);

        ActivateJobCompleteCommand("authUser", null);
        ActivateJobCompleteCommand("getUser", variables);
        ActivateJobCompleteCommand("updateUser", null);

        engine.waitForIdleState(Duration.ofSeconds(5));

        instanceAssert
                .hasPassedElement("get")
                .hasPassedElement("update")
                .hasNotPassedElement("register")
                .isCompleted();

    }

    @Test
    public void testRegisterOfInstance() throws Exception {

        Map<String, String>  variables  = new HashMap<String, String>(){{
            put("isUserExist", "false");
        }};

        String bpmnProcessId = initDeployment(NAME_BPMN);
        ProcessInstanceAssert instanceAssert = initProcessInstanceStart(bpmnProcessId);

        ActivateJobCompleteCommand("authUser", null);
        ActivateJobCompleteCommand("getUser", variables);
        ActivateJobCompleteCommand("registerUser", null);

        engine.waitForIdleState(Duration.ofSeconds(5));

        instanceAssert
                .hasPassedElement("get")
                .hasNotPassedElement("update")
                .hasPassedElement("register")
                .isCompleted();

    }

}
