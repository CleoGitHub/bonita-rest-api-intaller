package com.platform.management.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import org.bonitasoft.web.client.BonitaClient;
import org.bonitasoft.web.client.exception.ClientException;
import org.bonitasoft.web.client.log.LogContentLevel;
import org.bonitasoft.web.client.model.Profile;
import org.bonitasoft.web.client.model.User;
import org.bonitasoft.web.client.services.policies.OrganizationImportPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonitaDockerRunner {

    protected final Logger LOGGER = LoggerFactory.getLogger(BonitaDockerRunner.class);

    private Runner runner;
    private final Thread t;

    public BonitaDockerRunner() {
        runner = new Runner(LOGGER);
        t = new Thread(runner);
    }

    public void installOrga() {
        this.runner.installOrga();
    }

    public void terminate() {
        this.runner.terminate();
    }

    public int getPort() {
        return this.runner.getPort();
    }

    public void setTag(String tag) {
        runner.setTag(tag);
    }

    public void waitContainerIsReady() {
        synchronized (runner) {
            if(!runner.isReady()) {
                try {
                    LOGGER.debug("Waiting Bonita container to be ready");
                    runner.wait();
                    LOGGER.debug("Finish to wait for Bonita container to be ready");
                } catch (InterruptedException e) {
                    LOGGER.error("Error waiting Runner notification");
                    runner.terminate();
                }
            }
        }
    }

    public void run() {
        t.start();
    }

    private class Runner implements Runnable {

        private final Logger LOGGER;

        private String TAG = "latest";

        int MAX_PORT = 9999;

        int MIN_PORT = 7000;

        private int port;

        private String name;

        private boolean ready;

        private BonitaClient bonitaClient;

        public Runner(Logger LOGGER) {
            this.LOGGER = LOGGER;
            this.port = MIN_PORT;
            setNewName();
            this.ready = false;
        }

        public void init() {
            LOGGER.debug("Initialisation");
            if(port > MAX_PORT) {
                LOGGER.error("Unable to find available port between " + MIN_PORT + " and " + MAX_PORT);
                return;
            }

            Process proc = null;
            String cmd = "docker run --name " + name + " -p " + port + ":8080 bonita:" + TAG;
            try {
                proc = Runtime.getRuntime().exec(cmd);
            } catch (IOException e) {
                LOGGER.error("Error in execution of command: " + cmd);
                terminate();
            }

            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(proc.getInputStream()));

            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(proc.getErrorStream()));

            String s = null;
            boolean stop = false;
            try {
                s = stdInput.readLine();
            } catch (IOException e) {
                LOGGER.error("Error reading in ready output of Bonita container");
            }
            while (s != null) {
                LOGGER.debug(s);
                if(s.contains(getBonitaReadyMessage())) {
                    LOGGER.info("Bonita Container " + name + " is now ready");
                    synchronized (this) {
                        LOGGER.debug("Notify that the Bonita container is ready");
                        notify();
                        this.ready = true;
                    }
                }
                try {
                    s = stdInput.readLine();
                } catch (IOException e) {
                    LOGGER.error("Error reading in ready std output of Bonita container");
                }
            }

            try {
                s = stdError.readLine();
            } catch (IOException e) {
                LOGGER.error("Error reading in ready std error of Bonita container");
            }

            while (s != null && !stop) {
                LOGGER.debug("Error: " + s);
                if(s.contains("docker: Error response from daemon:")) {
                    stop = true;
                    handleDockerError(s);
                }
                try {
                    s = stdError.readLine();
                } catch (IOException e) {
                    LOGGER.error("Error reading in ready std error of Bonita container");
                }
            }
        }

        private void handleDockerError(String s) {
            LOGGER.debug("Handling docker error");
            if(s.contains("Conflict. The container name \"/" + name + "\" is already in use by container")) {
                LOGGER.info("name " + name + " is already used");
            } else if (s.contains("port is already allocated.")) {
                LOGGER.info("port " + port + " is already used");
                LOGGER.debug("Setting port to " + (++port));
            }

            cleanCurrentContainer();
            setNewName();

            this.init();
        }

        public void installOrga() {
            LOGGER.debug("installation of organisation on Bonita Container");

            if(bonitaClient == null)
                initBonitaClient();

            bonitaClient.login("install", "install");
            URL resource = getClass().getClassLoader().getResource("ACME.xml");

            if(resource == null) {
                LOGGER.error("file ACME.xml is missing");
                bonitaClient.logout();
                return;
            }

            LOGGER.debug("installation of ACME.xml on Bonita Container");
            try {
                bonitaClient.users().importOrganization(new File(resource.getFile()), OrganizationImportPolicy.MERGE_DUPLICATES);
            } catch (ClientException e) {
                LOGGER.debug(e.getStackTrace().toString());
                LOGGER.error("Error while installing ACME.xml");
                bonitaClient.logout();
                terminate();
                return;
            }
            LOGGER.info("ACME.xml installed on Bonita Container");

            User walter = bonitaClient.users().getUser("walter.bates");
            if(walter == null) {
                LOGGER.error("No user walter.bates in ACME.xml");
                terminate();
                return;
            }
            Profile adminProfil = bonitaClient.users().getProfileByName("Administrator");
            Profile userProfil = bonitaClient.users().getProfileByName("User");

            if(adminProfil == null) {
                LOGGER.error("No profile Administrator");
                terminate();
                return;
            }

            if(userProfil == null) {
                LOGGER.error("No profile User");
                terminate();
                return;
            }

            try {
                bonitaClient.users().addUserToProfile(walter.getId(), adminProfil.getId());
                bonitaClient.users().addUserToProfile(walter.getId(), userProfil.getId());
            } catch (ClientException e) {
                LOGGER.debug(e.toString());
                LOGGER.error("Error while giving to walter.bates User and Administrator profiles");
                bonitaClient.logout();
                terminate();
            }
        }

        private void initBonitaClient() {
            LOGGER.debug("Init bonitaClient");
            if(bonitaClient != null) {
                LOGGER.debug("Bonita client already exist");
                return;
            }

            bonitaClient = BonitaClient.builder("http://localhost:" + this.port + "/bonita")
                    .logContentLevel(LogContentLevel.OFF)
                    .build();
        }

        private void terminateBonitaClient() {
            LOGGER.debug("Terminate bonitaClient");
            if(bonitaClient == null) {
                LOGGER.debug("bonitaClient is null");
                return;
            }

            bonitaClient.logout();
            bonitaClient = null;
        }

        private void cleanCurrentContainer() {
            LOGGER.debug("Remove current container: " + name);
            String cmd = "docker rm -fv " + name;
            try {
                Runtime.getRuntime().exec(cmd);
            } catch (IOException e) {
                LOGGER.error("Error in execution of command: " + cmd);
            }
        }

        public void terminate() {
            LOGGER.info("Terminate Bonita Container");
            terminateBonitaClient();
            cleanCurrentContainer();
        }

        public boolean isReady() {
            return ready;
        }

        public int getPort() {
            return port;
        }

        private void setNewName() {
            name = "RestApiInstallerContainerTest" +  System.currentTimeMillis();
            LOGGER.debug("Setting container name to " + name);
        }

        public void setTag(String tag) {
            TAG = tag;
        }

        public String getBonitaReadyMessage() {
            String message = null;

            if(TAG.contains("7.12")) {
                message = "done";
            } else if(TAG.contains("7.13")) {
                message = "done";
            } else if(TAG.contains("7.14")) {
                message = "Server startup in";
            } else {
                message = "Server startup in";
            }

            return message;
        }

        @Override
        public void run() {
            this.init();
        }
    }
}
