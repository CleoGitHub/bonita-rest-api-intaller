package com.platform.management.model;

import java.io.File;
import java.net.URL;

import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.bonitasoft.web.client.BonitaClient;
import org.bonitasoft.web.client.log.LogContentLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestAPIInstaller extends AbstractConnector {

    protected final Logger LOGGER = LoggerFactory.getLogger(RestAPIInstaller.class);

    static final String INPUT_DOMAIN_NAME = "domainName";

    static final String INPUT_URL = "url";

    static final String INPUT_WORKER_USERNAME = "walter.bates";

    static final String INPUT_WORKER_PASSWORD = "bpm";

    static final String OUTPUT_STATUS = "status";

    static final String STATUS_FAILED = "failed";

    static final String STATUS_SUCCESS = "success";

    static BonitaClient bonitaClient;

    static final String API_REST_NAME = "reportingRestAPI-1.1.1.zip";

    /**
     * Perform validation on the inputs defined on the connector definition (src/main/resources/rest-api-installer.def)
     * You should: 
     * - validate that mandatory inputs are presents
     * - validate that the content of the inputs is coherent with your use case (e.g: validate that a date is / isn't in the past ...)
     */
    @Override
    public void validateInputParameters() throws ConnectorValidationException {
        checkMandatoryStringInput(INPUT_URL);
        checkMandatoryStringInput(INPUT_DOMAIN_NAME);
        checkMandatoryStringInput(INPUT_WORKER_USERNAME);
        checkMandatoryStringInput(INPUT_WORKER_PASSWORD);
    }

    protected void checkMandatoryStringInput(String inputName) throws ConnectorValidationException {
        try {
            String value = (String) getInputParameter(inputName);
            if (value == null || value.isEmpty()) {
                throw new ConnectorValidationException(this,
                        String.format("Mandatory parameter '%s' is missing.", inputName));
            }
        } catch (ClassCastException e) {
            throw new ConnectorValidationException(this, String.format("'%s' parameter must be a String", inputName));
        }
    }

    /**
     * Core method: 
     * - Execute all the business logic of your connector using the inputs (connect to an external service, compute some values ...).
     * - Set the output of the connector execution. If outputs are not set, connector fails.
     */
    @Override
    protected void executeBusinessLogic() {
        LOGGER.debug("Executing business logic");

        URL resource = getClass().getClassLoader().getResource(API_REST_NAME);

        if(resource == null) {
            setOutputParameter(OUTPUT_STATUS, STATUS_FAILED);
            LOGGER.error("Resource " + API_REST_NAME + " is missing");
            bonitaClient.logout();
            return;
        }

        try {
            bonitaClient.applications().importPage(new File(resource.getFile()));
        } catch (Exception e) {
            setOutputParameter(OUTPUT_STATUS, STATUS_FAILED);
            LOGGER.error("While importing resource: " + API_REST_NAME);
        }

        setOutputParameter(OUTPUT_STATUS, STATUS_SUCCESS);
    }

    /**
     * [Optional] Open a connection to remote server
     */
    public void connect() {
        bonitaClient = BonitaClient.builder((String) getInputParameter(INPUT_URL))
                .logContentLevel(LogContentLevel.OFF)
                .build();
        bonitaClient.login((String) getInputParameter(INPUT_WORKER_USERNAME), (String) getInputParameter(INPUT_WORKER_PASSWORD));
    }

    /**
     * [Optional] Close connection to remote server
     */
    public void disconnect() {
        if (bonitaClient != null) {
            bonitaClient.logout();
        }
    }
}
