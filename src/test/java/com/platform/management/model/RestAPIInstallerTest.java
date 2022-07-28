package com.platform.management.model;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.bonitasoft.web.client.log.LogContentLevel;
import org.bonitasoft.web.client.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import org.bonitasoft.web.client.BonitaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bonitasoft.web.client.exception.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class RestAPIInstallerTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(RestAPIInstallerTest.class);

    RestAPIInstaller connector;

    Map<String, Object> parameters;

    BonitaDockerRunner bonitaDockerRunner;

    String[] inputs = new String[] {
            RestAPIInstaller.INPUT_DOMAIN_NAME,
            RestAPIInstaller.INPUT_URL,
            RestAPIInstaller.INPUT_WORKER_USERNAME,
            RestAPIInstaller.INPUT_WORKER_PASSWORD
    };

    String[] tags = new String[] {
            "7.12",
            "7.13",
            "7.14"
    };

    @BeforeEach
    void setUp() {
        connector = new RestAPIInstaller();

        parameters = new HashMap<>();
        parameters.put(RestAPIInstaller.INPUT_DOMAIN_NAME, "test");
        parameters.put(RestAPIInstaller.INPUT_URL, "http://localhost:8080/bonita");
        parameters.put(RestAPIInstaller.INPUT_WORKER_USERNAME, "walter.bates");
        parameters.put(RestAPIInstaller.INPUT_WORKER_PASSWORD, "bpm");
    }

    @Test
    void should_throw_exception_if_mandatory_input_is_missing() {
        Map<String, Object> wrongParameters;

        for(String input : inputs) {
            wrongParameters = cloneMap(parameters);
            wrongParameters.remove(input);
            connector.setInputParameters(wrongParameters);
            assertThrows(ConnectorValidationException.class, () ->
                    connector.validateInputParameters()
            );
            connector = new RestAPIInstaller();
        }
    }

    @Test
    void should_throw_exception_if_mandatory_input_is_empty() {
        Map<String, Object> wrongParameters;

        for(String input : inputs) {
            wrongParameters = cloneMap(parameters);
            wrongParameters.put(input, "");
            connector.setInputParameters(wrongParameters);
            assertThrows(ConnectorValidationException.class, () ->
                    connector.validateInputParameters()
            );
        }
    }

    @Test
    void testConnectorShouldInstallRestAPIExtension() {
        for(String tag: tags) {
            prepareBonitaContainer(tag);

            connector.connect();
            connector.executeBusinessLogic();

            BonitaClient bonitaClient = BonitaClient.builder("http://localhost:" + bonitaDockerRunner.getPort() + "/bonita")
                    .logContentLevel(LogContentLevel.OFF)
                    .build();

            bonitaClient.login("install", "install");

            Page apiRestExtension = null;

            try {
                apiRestExtension = bonitaClient.applications().getPage("custompage_reportingRestAPI");
            } catch (NotFoundException e) {
                bonitaDockerRunner.terminate();
                bonitaClient.logout();
                connector.disconnect();
                fail("Rest api not installed");
            }
            bonitaClient.logout();
            connector.disconnect();
            bonitaDockerRunner.terminate();

            assertThat(apiRestExtension).isNotNull();
        }
    }



//    @Test
//    void testOutputStatusShouldBeFailedIfResourceNotExist() {
//        prepareBonitaContainer();
//
//        connector.connect();
//        connector.executeBusinessLogic();
//
//        assertThat(connector.getOutputParameters(RestAPIInstaller.OUTPUT_STATUS)).isEqualTo(RestAPIInstaller.STATUS_FAILED);
//
//        connector.disconnect();
//        bonitaDockerRunner.terminate();
//    }

    void prepareBonitaContainer() {
        bonitaDockerRunner = new BonitaDockerRunner();
        bonitaDockerRunner.run();
        bonitaDockerRunner.waitContainerIsReady();
        bonitaDockerRunner.installOrga();

        parameters.put(RestAPIInstaller.INPUT_URL, "http://localhost:" + bonitaDockerRunner.getPort() + "/bonita");
        connector.setInputParameters(parameters);
    }


    void prepareBonitaContainer(String tag) {
        bonitaDockerRunner = new BonitaDockerRunner();
        bonitaDockerRunner.setTag(tag);
        bonitaDockerRunner.run();
        bonitaDockerRunner.waitContainerIsReady();
        bonitaDockerRunner.installOrga();

        parameters.put(RestAPIInstaller.INPUT_URL, "http://localhost:" + bonitaDockerRunner.getPort() + "/bonita");
        connector.setInputParameters(parameters);
    }

    Map<String, Object> cloneMap(Map<String, Object> mapToClone) {
        return new HashMap<>(mapToClone);
    }
}