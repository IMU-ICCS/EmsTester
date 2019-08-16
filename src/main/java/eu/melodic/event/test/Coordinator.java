package eu.melodic.event.test;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.buildobjects.process.ProcBuilder;
import org.buildobjects.process.ProcResult;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
@Component
public class Coordinator implements InitializingBean {

    @Value("${ems.base-url}")
    private String emsUrl;
    @Value("${ems.truststore.file}")
    private String emsTrustStoreFile;
    @Value("${ems.truststore.password}")
    private String emsTrustStorePassword;
    @Value("${client.properties.template}")
    private String clientPropertiesTemplate;
    @Value("${client.properties.delete-on-exit}")
    private boolean deletePropertiesOnExit;
    @Value("${client.properties.dir}")
    private String clientPropertiesDir;
    @Value("${client.launch.command}")
    private String clientLaunchCommand;
    @Value("${client.work.dir}")
    private String clientWorkDir;
    @Value("${client.install.dir}")
    private String clientInstallationDir;
    @Value("${client.install.skip:false}")
    private boolean skipClientInstallation;
    @Value("${client.install.dir.clean:false}")
    private boolean cleanClientInstallation;

    @Value("${client.broker.port.start}")
    private int clientBrokerPortStart;
    @Value("${client.broker.port.incr}")
    private int clientBrokerPortIncrease;
    @Value("${client.broker.port2.start}")
    private int clientBrokerPort2Start;
    @Value("${client.broker.port2.incr}")
    private int clientBrokerPort2Increase;
    @Value("${client.jmx.port.start}")
    private int clientJmxPortStart;
    @Value("${client.jmx.port.incr}")
    private int clientJmxPortIncrease;

    @Value("${delay.default:5}")
    private int delayDefault;
    @Value("${delay.metric-value-update:-1}")
    private int delayMetricValueUpdate;
    @Value("${delay.preregistration:-1}")
    private int delayPreregistration;
    @Value("${delay.client-installation:-1}")
    private int delayClientInstallation;
    @Value("${delay.node-deployment:-1}")
    private int delayNodeDeployment;
    @Value("${delay.event-generation:-1}")
    private int delayEventGeneration;

    @Value("${model.camel}")
    private String camelModelName;
    @Value("${model.cp}")
    private String cpModelName;
    @Value("${node.info}")
    private String nodeInfoJson;
    @Value("${event.rules}")
    private String eventRulesJson;

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private TaskExecutor taskExecutor;

    private AtomicInteger counter = new AtomicInteger(0);
    private AtomicInteger clientsExited = new AtomicInteger(0);

    private List<NodeInfo> nodeInfoList;
    private List<EventRule> eventRulesList;

    @Override
    public void afterPropertiesSet() throws Exception {
        initNodeInfo();
        initEventRules();
    }

    private void initNodeInfo() {
        log.info("-----------> initNodeInfo(): node-info from Json: {}", nodeInfoJson);
        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<NodeInfo>>(){}.getType();
        nodeInfoList = gson.fromJson(nodeInfoJson, listType);
        log.info("-----------> initNodeInfo(): node-info deserialized: {}", nodeInfoList);
    }

    private void initEventRules() {
        log.info("-----------> initEventRules(): event-rules from Json: {}", eventRulesJson);
        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<EventRule>>(){}.getType();
        eventRulesList = gson.fromJson(eventRulesJson, listType);
        log.info("-----------> initEventRules(): event-rules deserialized: {}", eventRulesList);
    }

    @Bean
    RestTemplate restTemplate() throws Exception {
        FileSystemResource emsTrustStore = new FileSystemResource(emsTrustStoreFile);
        SSLContext sslContext = new SSLContextBuilder()
                .loadTrustMaterial(emsTrustStore.getURL(), emsTrustStorePassword.toCharArray())
                .build();
        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext);
        HttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(socketFactory)
                .build();
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(factory);
    }

    @Async
    void startProcess() throws MalformedURLException, URISyntaxException {
        log.info("-----------> startProcess: {}");
        RestTemplate restTemplate = applicationContext.getBean(RestTemplate.class);

        // Invoke EMS to process CAMEL model
        String url = emsUrl+"/camelModel";
        log.info("-----------> EMS-URL: {}", url);
        String requestBodyJson =
                "{\n" +
                "  \"applicationId\": \""+camelModelName+"\",\n" +
                "  \"notificationURI\": \"notificationUri"+System.currentTimeMillis()+"\",\n" +
                "  \"watermark\": {\n" +
                "      \"user\": \"mprusinski\",\n" +
                "      \"system\": \"mule\",\n" +
                "      \"uuid\": \"9d65b0b6-40a8-11e7-a919-92ebcb67fe33\"\n" +
                "  }\n" +
                "}";
        log.info("-----------> Request Body: {}", requestBodyJson);

        //String responseBody = restTemplate.postForObject(url, requestBody, String.class);
        RequestEntity<String> requestEntity = RequestEntity
                .post(new URL(url).toURI())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBodyJson);
        ResponseEntity<String> responseBody = restTemplate.exchange(requestEntity, String.class);
        log.info("-----------> Response Body: {}", responseBody);

        int httpCode = responseBody.getStatusCodeValue();
        if (httpCode<200 && httpCode>299) throw new RuntimeException("Start process request was not successful: "+responseBody);

        log.info("Waiting EMS to process CAMEL model...");
    }

    void updateMetricValues() throws MalformedURLException, URISyntaxException {
        log.info("-----------> updateMetricValues():");
        RestTemplate restTemplate = applicationContext.getBean(RestTemplate.class);

        // Invoke EMS to process CAMEL model
        String url = emsUrl+"/cpModelJson";
        log.info("-----------> EMS-URL: {}", url);
        String requestBodyJson =
                "{ \"cp-model-id\": \""+cpModelName+"\" }";
        log.info("-----------> Request Body: {}", requestBodyJson);

        RequestEntity<String> requestEntity = RequestEntity
                .post(new URL(url).toURI())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBodyJson);
        ResponseEntity<String> responseBody = restTemplate.exchange(requestEntity, String.class);
        log.info("-----------> Response Body: {}", responseBody);

        int httpCode = responseBody.getStatusCodeValue();
        if (httpCode<200 && httpCode>299) throw new RuntimeException("Update Metric Values request was not successful: "+responseBody);

        log.info("Waiting EMS to extract Metric Values from CP model...");
    }

    void preregisterNodes(List<NodeInfo> nodes) throws MalformedURLException, URISyntaxException {
        log.info("-----------> preregisterNodes():");
        for (NodeInfo nodeInfo : nodes) {
            preregisterNode(nodeInfo);
        }
        log.info("-----------> preregisterNodes(): DONE");
    }

    private void preregisterNode(NodeInfo nodeInfo) throws MalformedURLException, URISyntaxException {
        log.info("-----------> preregisterNode():");
        RestTemplate restTemplate = applicationContext.getBean(RestTemplate.class);

        // Invoke EMS to process CAMEL model
        String url = emsUrl+"/baguette/registerNode";
        log.info("-----------> EMS-URL: {}", url);
        String requestBodyJson = String.format(
                "{\n"+
                "    'id': '%s',\n"+
                "    'type': 'VM',\n"+
                "    'name': '%s',\n"+
                "    'providerId': '%s',\n"+
                "    'ip': '%s',\n"+
                "    'operatingSystem': 'ubuntu',\n"+
                "    'random': '1234567890'\n"+
                "}",
                nodeInfo.getId(), nodeInfo.getId().replace("-"," "),
                nodeInfo.getProvider(), nodeInfo.getIp(), System.currentTimeMillis()
        );
        log.info("-----------> Request Body: {}", requestBodyJson);

        RequestEntity<String> requestEntity = RequestEntity
                .post(new URL(url).toURI())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBodyJson);
        ResponseEntity<String> responseBody = restTemplate.exchange(requestEntity, String.class);
        log.info("-----------> Response Body: {}", responseBody);

        int httpCode = responseBody.getStatusCodeValue();
        if (httpCode<200 && httpCode>299) throw new RuntimeException("Preregister Node request was not successful: "+responseBody);
    }

    public void installClient() throws Exception {
        log.info("-----------> installClient():");

        // Clean up previous client installation
        if (cleanClientInstallation) {
            log.info("-----------> installClient(): Cleaning any previous client installation");
            File oldClientDir = new File(clientWorkDir);
            FileUtils.deleteDirectory(oldClientDir);
        }

        // Download baguette client archive from EMS
        String downloadUrl = emsUrl+"/resources/baguette-client.tgz";
        log.info("-----------> installClient(): Downloading client installation archive from: {}", downloadUrl);
        HttpHeaders headers = restTemplate().headForHeaders(downloadUrl);
        log.info("-----------> installClient(): Accept-Ranges={}", headers.get("Accept-Ranges"));
        log.info("-----------> installClient(): Content-Type={}", headers.getContentType());
        log.info("-----------> installClient(): Content-Length={}", headers.getContentLength());
        log.info("-----------> installClient(): Content-Length={}", headers.getContentDisposition().getFilename());
        boolean acceptRanges = headers.get("Accept-Ranges").contains("bytes");
        long contentLength = headers.getContentLength();
        String fileName = "baguette-client.";

        File file = File.createTempFile(fileName, ".tgz", new File("tmp"));
        File archiveFile = restTemplate().execute(downloadUrl, HttpMethod.GET,
                acceptRanges
                        ? clientHttpRequest -> clientHttpRequest.getHeaders().set(
                        "Range",
                        String.format("bytes=%d-%d", file.length(), contentLength))
                        : null,
                clientHttpResponse -> {
                    StreamUtils.copy(clientHttpResponse.getBody(), new FileOutputStream(file, true));
                    return file;
                });
        log.info("-----------> installClient(): archive={}", archiveFile);

        // Uncompress archive to installation directory
        log.info("-----------> installClient(): Extracting archive to: {}", clientInstallationDir);
        File destination = new File(clientInstallationDir);
        Archiver archiver = ArchiverFactory.createArchiver("tar", "gz");
        archiver.extract(archiveFile, destination);

        log.info("-----------> installClient(): DONE");
    }

    public void deployNodes(List<NodeInfo> nodes) throws IOException {
        log.info("-----------> deployNodes():");
        for (NodeInfo nodeInfo : nodes) {
            deployNode(nodeInfo);
        }
        log.info("-----------> deployNodes(): DONE");
    }

    private void deployNode(NodeInfo nodeInfo) throws IOException {
        String id = nodeInfo.getId();
        String ip = nodeInfo.getIp();
        log.info("-----------> deployNode(): String baguette client for: id={}, ip={}", id, ip);

        // Prepare baguette client properties
        InputStream isPropertiesTpl = applicationContext.getResource(clientPropertiesTemplate).getInputStream();
        String propertiesTpl = new Scanner(isPropertiesTpl).useDelimiter("\\Z").next();
        String propertiesStr = String.format(propertiesTpl, id, ip);
        log.info("-----------> deployNode(): Configuration:\n{}", propertiesStr);

        // Create baguette client properties file
        File baseDir = new File(clientPropertiesDir);
        File propertiesFile = File.createTempFile("client_"+id+"_", ".properties", baseDir);
        if (deletePropertiesOnExit) {
            propertiesFile.deleteOnExit();
        }
        log.info("-----------> deployNode(): Configuration file: {}", propertiesFile);
        try (PrintWriter pw = new PrintWriter(new FileWriter(propertiesFile))) {
            pw.println(propertiesStr);
        }

        // Select broker and JMX ports for this client
        int cnt = counter.getAndIncrement();
        int brokerPort = clientBrokerPortStart + cnt * clientBrokerPortIncrease;
        int brokerPort2 = clientBrokerPort2Start + cnt * clientBrokerPort2Increase;
        int jmxPort = clientJmxPortStart + cnt * clientJmxPortIncrease;
        log.info("-----------> deployNode(): Ports: cnt={}, broker={},{}, jmx={}", cnt, brokerPort, brokerPort2, jmxPort);

        // Call baguette client passing properties file
        log.info("############ {}:   Queueing task for client: {}", id, id);
        taskExecutor.execute(() -> {
            List<String> argsList = Arrays.asList(clientLaunchCommand.split("[ \t]+"));
            String cmdStr = argsList.remove(0);

            boolean found1 = false;
            boolean found2 = false;
            File clientOutputFile = new File(clientPropertiesDir, "client_"+id+"_"+System.currentTimeMillis()+".txt");
            for (int i=0; i<argsList.size(); i++) {
                if ("{PROPERTIES}".equalsIgnoreCase(argsList.get(i))) {
                    argsList.set(i, propertiesFile.getAbsolutePath());
                    found1 = true;
                } else
                if ("{PORTS}".equalsIgnoreCase(argsList.get(i))) {
                    argsList.set(i, "--brokercep.broker-port="+brokerPort);
                    argsList.add(i+1, "--brokercep.broker-url-2=tcp://localhost:"+brokerPort2);
                    argsList.add(i+2, "--brokercep.connector-port="+jmxPort);
                    found2 = true;
                } else
                if ("{OUTPUT}".equalsIgnoreCase(argsList.get(i))) {
                    argsList.set(i, clientOutputFile.getAbsolutePath());
                }
            }
            if (!found1) {
                argsList.add(propertiesFile.getAbsolutePath());
            }
            if (!found2) {
                argsList.add("--brokercep.broker-port="+brokerPort);
                argsList.add("--brokercep.broker-url-2=tcp://localhost:"+brokerPort2);
                argsList.add("--brokercep.connector-port="+jmxPort);
            }

            log.info("############ {}:   STARTING baguette-client: {} {}", id, cmdStr, argsList);
            try {
                ProcResult result = new ProcBuilder(clientLaunchCommand)
                        .withArgs((String[]) argsList.toArray(new String[0]))
                        //.withVar("", "")
                        .withNoTimeout()
                        .ignoreExitStatus()
                        //.withExpectedExitStatuses(0, 100)
                        .withWorkingDirectory(new File(clientWorkDir))
                        .run();
                log.info("############ {}:   EXITED baguette-client", id);
                log.info("############ {}:   result:", id);
                log.info("############ {}:   - proc-str: {}", id, result.getProcString());
                log.info("############ {}:   - exit-val: {}", id, result.getExitValue());
                log.info("############ {}:   - exec-dur: {}", id, result.getExecutionTime());
                log.info("############ {}:   - out-str:  {}", id, StringUtils.substring(result.getOutputString(), 0, 50));
                log.info("############ {}:   - err-str:  {}", id, StringUtils.substring(result.getErrorString(), 0, 50));

                clientsExited.incrementAndGet();
            } catch (Exception e) {
                log.error("############ {}:   EXCEPTION: ", id, e);
            }
        });
        log.info("############ {}:   Queueing task for client: DONE", id);
    }

    public int clientsStarted() {
        return counter.get();
    }

    public int clientsRunning() {
        return counter.get() - clientsExited.get();
    }

    private static final String URL_EVENT_GEN_START = "/event/generate-start/%s/%s/%d/%f-%f";
    private static final String URL_EVENT_GEN_STOP = "/event/generate-stop/%s/%s";
    private static final String URL_EVENT_SEND = "/event/send/%s/%s/%f";

    @Async
    private void generateEvents() throws Exception {
        log.info("-----------> generateEvents(): Scheduling event generation rules...");

        eventRulesList.forEach(rule -> {
            taskExecutor.execute(() -> {
                String ruleId = rule.getRuleId();
                long initDelay = rule.getInitDelay();
                EventRuleCommand command = rule.getCommand();
                String clientId = rule.getClientId();
                String topicName = rule.getTopic();
                long interval = rule.getInterval();
                double lowerValue = rule.getLowValue();
                double upperValue = rule.getHighValue();
                double value = rule.getValue();

                // Prepare event command url
                String url = null;
                if (command==EventRuleCommand.GEN_START) {
                    url = emsUrl + String.format(URL_EVENT_GEN_START,
                            clientId, topicName, interval, lowerValue, upperValue);
                } else
                if (command==EventRuleCommand.GEN_STOP) {
                    url = emsUrl + String.format(URL_EVENT_GEN_STOP, clientId, topicName);
                } else
                if (command==EventRuleCommand.SEND) {
                    url = emsUrl + String.format(URL_EVENT_SEND, clientId, topicName, value);
                }
                url = url.replace(",", ".");

                // Wait for initial delay
                if (initDelay>0) {
                    log.info("-----------> generateEvents(): {}: Initial delay: {}sec", ruleId, initDelay);
                    try { Thread.sleep(1000L*initDelay); } catch (InterruptedException e) { e.printStackTrace(); }
                }

                // Send event command
                log.info("-----------> generateEvents(): {}: Sending request: {}", ruleId, url);
                String response = null;
                try {
                    response = restTemplate().getForObject(url, String.class);
                } catch (Exception e) {
                    log.error("-----------> generateEvents(): {}: Sending request failed: ", ruleId, e);
                }
                log.info("-----------> generateEvents(): {}: Server response: {}", ruleId, response);
            });
        });

        log.info("-----------> generateEvents(): DONE");
    }

    private void monitorClientTermination(boolean exitAfterClientTermination) {
        log.info("-----------> monitorClientTermination(): clients running: {}", clientsRunning());
        taskExecutor.execute(() -> {
            while (clientsRunning()>0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    log.warn("-----------> monitorClientTermination(): Client monitoring Interrupted: ", e);
                }
            }
            log.info("-----------> monitorClientTermination(): All clients terminated");
            if (exitAfterClientTermination) {
                log.info("-----------> monitorClientTermination(): Exiting system...");
                System.exit(0);
            }
        });
    }

    // -----------------------------------------------------------------------------------------------------------------

    protected void scheduleMetricValuesUpdate() {
        delayedExecution(delayMetricValueUpdate, "scheduleMetricValuesUpdate", "updateMetricValues",
                (value) -> {
                    try {
                        updateMetricValues();
                        scheduleNodePreregistration();
                    } catch (Exception e) {
                        log.error("{}(): TimerTask failed for updateMetricValues(): ", value, e);
                    }
                });
    }

    protected void scheduleNodePreregistration() {
        delayedExecution(delayPreregistration, "scheduleNodePreregistration", "preregisterNodes",
                (value) -> {
                    try {
                        log.info("{}(): Calling preregisterNodes()...", value);
                        preregisterNodes(nodeInfoList);
                        log.info("{}(): Calling preregisterNodes()... DONE", value);

                        if (skipClientInstallation) {
                            log.info("{}(): Skipping client installation", value);
                            scheduleNodeDeployment();
                        } else {
                            scheduleClientInstallation();
                        }
                    } catch (Exception e) {
                        log.error("{}(): TimerTask failed for preregisterNodes(): ", value, e);
                    }
                });
    }

    protected void scheduleClientInstallation() {
        delayedExecution(delayClientInstallation, "scheduleClientInstallation", "installClient",
                (value) -> {
                    try {
                        log.info("{}(): Calling installClient()...", value);
                        installClient();
                        log.info("{}(): Calling installClient()... DONE", value);

                        scheduleNodeDeployment();
                    } catch (Exception e) {
                        log.error("{}(): TimerTask failed for installClient(): ", value, e);
                    }
                });
    }

    private void scheduleNodeDeployment() {
        delayedExecution(delayNodeDeployment, "scheduleNodeDeployment", "deployNode",
                (value) -> {
                    try {
                        log.info("{}(): Calling deployNode()...", value);
                        deployNodes(nodeInfoList);
                        log.info("{}(): Calling deployNode()... DONE", value);

                        monitorClientTermination(true);
                        scheduleEventGeneration();
                    } catch (Exception e) {
                        log.error("{}(): TimerTask failed for deployNode(): ", value, e);
                    }
                });
    }

    private void scheduleEventGeneration() {
        delayedExecution(delayEventGeneration, "scheduleEventGeneration", "generateEvents",
                (value) -> {
                    try {
                        log.info("{}(): Calling generateEvents()...", value);
                        generateEvents();
                        log.info("{}(): Calling generateEvents()... DONE", value);
                    } catch (Exception e) {
                        log.error("{}(): TimerTask failed for generateEvents(): ", value, e);
                    }
                });
    }

    protected void delayedExecution(int seconds, String caller, String callee, Consumer<String> task) {
        if (seconds<0) {
            log.info("{}(): Delay is negative or not provided: {}. Using default delay", caller, seconds);
            seconds = delayDefault;
        }
        log.info("{}(): Sleeping for {}sec", caller, seconds);
        Timer timer = new Timer("Timer_"+System.currentTimeMillis());
        timer.schedule(new TimerTask() {
            public void run() {
                log.info("Invoking {}():...", callee);
                try {
                    task.accept(callee);
                } catch (Exception e) {
                    log.error("{}(): TimerTask failed: ", callee, e);
                }
            }
        }, seconds*1000L);
    }

    // -----------------------------------------------------------------------------------------------------------------

    @Data
    static class NodeInfo {
        private final String id;
        private final String ip;
        private final String provider;

        public Map<String,String> toMap() {
            return new HashMap<String,String>() {{
                put("id", id);
                put("ip", ip);
                put("provider", provider);
            }};
        }
    }

    enum EventRuleCommand {
        GEN_START, GEN_STOP, SEND
    }

    @Data
    static class EventRule {
        @SerializedName("rule-id")
        private final String ruleId;
        @SerializedName("init-delay")
        private final long initDelay;
        @SerializedName("command")
        private final EventRuleCommand command;
        @SerializedName("client-id")
        private final String clientId;
        @SerializedName("topic")
        private final String topic;
        @SerializedName("interval")
        private final long interval;
        @SerializedName("low-value")
        private final double lowValue;
        @SerializedName("high-value")
        private final double highValue;
        @SerializedName("value")
        private final double value;
    }
}
