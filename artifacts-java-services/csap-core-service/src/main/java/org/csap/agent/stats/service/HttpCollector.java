package org.csap.agent.stats.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.util.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.csap.agent.CsapApis;
import org.csap.agent.CsapConstants;
import org.csap.agent.model.Application;
import org.csap.agent.model.ContainerState;
import org.csap.agent.model.ModelJson;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.stats.ServiceCollector;
import org.csap.agent.ui.rest.file.FileApiUtils;
import org.csap.helpers.CSAP;
import org.csap.helpers.CsapApplication;
import org.csap.integations.micrometer.CsapMicroMeter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpCollector {

    public static final String CSAP_SERVICE_COUNT = "csapServiceCount";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    ObjectMapper jacksonMapper = new ObjectMapper();

    CsapApis csapApis;
    private ServiceCollector serviceCollector;

    private ObjectNode deltaLastCollected = jacksonMapper.createObjectNode();

    public HttpCollector(
            CsapApis csapApis,
            ServiceCollector serviceCollector
    ) {

        this.csapApis = csapApis;

        this.serviceCollector = serviceCollector;

    }

    public void collectServicesOnHost() {

        csapApis.application()
                .getActiveProject()
                .getServicesOnHost(csapApis.application().getCsapHostName())
                .filter(serviceInstance -> serviceInstance.isHttpCollectionEnabled())
                .forEach(this::executeHttpCollection);

    }

    private void executeHttpCollection(ServiceInstance serviceInstance) {

        logger.debug("{} titles: {} ", serviceInstance.toSummaryString(), serviceInstance.getServiceMeterTitles());

        int containerNumber = 1;

        for (var container : serviceInstance.getContainerStatusList()) {

            var containerId = serviceInstance.getName();

            // String containerId = serviceInstance.getServiceName_Port() ;
            if (serviceInstance.is_cluster_kubernetes()) {

                containerId = serviceInstance.getName() + "-" + containerNumber;

                if (!container.isRunning()) {

                    // hook for skipping data collection on hosts where kubernetes container is
                    // inactive
                    continue;

                }

            }

            var applicationResults = new ServiceCollectionResults(serviceInstance, serviceCollector
                    .getInMemoryCacheSize());

            // initialized to be 0
            serviceInstance
                    .getServiceMeters()
                    .stream()
                    .filter(meter -> meter.getMeterType().isHttp())
                    .forEach(serviceMeter -> applicationResults.addCustomResultLong(serviceMeter.getCollectionId(),
                            0l));

            if (!container.isRunning() && !csapApis.application().isJunit()) {

                logger.debug("{} Skipping collections as service is down", serviceInstance.getName());
                container.setHealthReportCollected(null);

                // If java is configured - 0 out the collected values
                var javaCollectionUrl = serviceInstance.getHttpCollectionSettings()
                        .path(ModelJson.javaCollectionUrl.jpath())
                        .asText();
                if (StringUtils.isNotEmpty(javaCollectionUrl)) {
                    applicationResults.add_results_to_java_collection(serviceCollector.getServiceToJavaMetrics(), containerId);
                }

            } else {

                performCollection(serviceInstance, container, containerId, applicationResults);

            }

            // will update based on collected values.
            applicationResults.add_results_to_application_collection(serviceCollector.getServiceToAppMetrics(),
                    containerId);

            // other collection intervals will reuse the data from shorter intervals
            serviceCollector.getLastCollectedResults().put(
                    containerId,
                    applicationResults);

            containerNumber++;

        }

    }

    private void performCollection(
            ServiceInstance serviceInstance,
            ContainerState container,
            String containerId,
            ServiceCollectionResults applicationResults) {

        var allTimer = csapApis.metrics().startTimer();
        var containerTimer = csapApis.metrics().startTimer();
        var httpCollectionSettions = serviceInstance.getHttpCollectionSettings();

        try {

            var httpCollectionUrl = httpCollectionSettions
                    .path(ModelJson.httpCollectionUrl.jpath()).asText();

            httpCollectionUrl = serviceInstance.resolveRuntimeVariables(httpCollectionUrl);

            var javaCollectionUrl = httpCollectionSettions.path(ModelJson.javaCollectionUrl.jpath())
                    .asText();
            var healthCollectionUrl = httpCollectionSettions.path(ModelJson.healthCollectionUrl.jpath())
                    .asText();
            var patternMatch = httpCollectionSettions.path("patternMatch").asText();

            ResponseEntity<String> collectionResponse = null;

            if (StringUtils.isNotEmpty(httpCollectionUrl)) {

                collectionResponse = perform_collection(serviceInstance, httpCollectionUrl,
                        httpCollectionSettions,
                        container);

                if (collectionResponse != null && collectionResponse
                        .getStatusCode()
                        .is2xxSuccessful()) {

                    processApplicationCollection(serviceInstance, containerId, applicationResults,
                            httpCollectionSettions,
                            patternMatch,
                            collectionResponse);

                } else {

                    logger.warn("Unable to collectionResponse: {}", collectionResponse);

                }

            }

            var usingJavaJmxForSource = serviceInstance.isJavaJmxCollectionEnabled();
            logger.debug( "{} usingJavaJmxForSource: {} ", serviceInstance.getName(), usingJavaJmxForSource  ) ;
            if ( !usingJavaJmxForSource && StringUtils.isNotEmpty(javaCollectionUrl) ) {

                if (collectionResponse == null || !javaCollectionUrl.equals(httpCollectionUrl)) {

                    collectionResponse = perform_collection(serviceInstance, javaCollectionUrl,
                            httpCollectionSettions,
                            container);

                }

                if (collectionResponse != null && collectionResponse
                        .getStatusCode()
                        .is2xxSuccessful()) {

                    var jsonResponse = jacksonMapper.readTree(collectionResponse.getBody());
                    processJavaCollection(
                            serviceInstance.is_tomcat_collect(),
                            containerId,
                            applicationResults,
                            jsonResponse);

                } else {

                    logger.warn("Unable to collectionResponse: {}", collectionResponse);

                }

            }

            if (StringUtils.isNotEmpty(healthCollectionUrl)) {

                if (collectionResponse == null || !javaCollectionUrl.equals(healthCollectionUrl)) {

                    collectionResponse = perform_collection(serviceInstance, healthCollectionUrl,
                            httpCollectionSettions,
                            container);

                }

                if (collectionResponse != null && collectionResponse
                        .getStatusCode()
                        .is2xxSuccessful()) {

                    JsonNode healthResponse = jacksonMapper.readTree(collectionResponse.getBody());
                    JsonNode healthReport = healthResponse.path(
                            CsapMicroMeter.HealthReport.Report.name.json);

                    if (healthReport.isObject()) {

                        container.setHealthReportCollected((ObjectNode) healthReport);

                    }

                } else {

                    logger.warn("Unable to collectionResponse: {}", collectionResponse);

                }

            }

        } catch (Exception e) {

            csapApis.metrics().incrementCounter("csap.collect-http.failures");
            csapApis.metrics().incrementCounter("collect-http." + serviceInstance.getName()
                    + ".failures");
            var message = "Collection failed for service '{}' \n configuration: {} \n\n reason: \"{}\";  ==> verify collection settings in definition";

            if (!csapApis.application().isDesktopProfileActiveOrSpringNull()) {

                logger.warn(message,
                        serviceInstance.getName(), httpCollectionSettions, e.getMessage());

            }

            logger.debug("Reason: {} ", CSAP.buildCsapStack(e));

        } finally {

            csapApis.metrics().stopTimer(containerTimer, "collect-http." + containerId);
            csapApis.metrics().stopTimer(allTimer, "csap.collect-http");

        }
    }

    private void processApplicationCollection(
            ServiceInstance serviceInstance,
            String containerId,
            ServiceCollectionResults applicationResults,
            ObjectNode httpConfig,
            String patternMatch,
            ResponseEntity<String> collectionResponse
    )
            throws IOException {

        logger.debug("{} processing using: {}", serviceInstance.getName(), patternMatch);

        if (patternMatch.equalsIgnoreCase("JSON")) {

            JsonNode jsonResponse = jacksonMapper.readTree(collectionResponse.getBody());

            serviceInstance
                    .getServiceMeters()
                    .stream()
                    .forEach(
                            serviceMeter -> processHttpMeterUsingJson(serviceInstance.getName(), serviceMeter,
                                    containerId, patternMatch,
                                    applicationResults,
                                    httpConfig, jsonResponse));

        } else {

            var textResponse = collectionResponse.getBody();

            // trim comments (lines starting with #) and empty lines
            textResponse = textResponse.replaceAll("(?m)^#.*\n", "");
            textResponse = textResponse.replaceAll("(?m)^[ \t]*\n", "");

            // logger.info("collected response: \n {} ", textResponse) ;

            final String collectedData = textResponse;

            serviceInstance
                    .getServiceMeters()
                    .stream()
                    .filter(ServiceMeter::isHttp)
                    .forEach(serviceMeter -> processHttpMeterUsingRegex(
                            serviceInstance.getName(),
                            serviceMeter, containerId, patternMatch,
                            applicationResults,
                            httpConfig, collectedData));

        }

    }

    Map<String, Long> lastOpenFiles = new HashMap<>();
    Map<String, Long> lastClassesLoaded = new HashMap<>();
    Map<String, Long> lastClassesUnLoaded = new HashMap<>();

    private void processJavaCollection(
            boolean isTomcat,
            String containerId,
            ServiceCollectionResults applicationResults,
            JsonNode collectionReport
    ) {

        logger.debug("{} isTomcat: {}", applicationResults.getServiceInstance().getName(), isTomcat);

        applicationResults.setCpuPercent(Math.round(collectionReport.at("/process.cpu.usage").asDouble()
                * 100));

        applicationResults.setJvmThreadCount(collectionReport.at("/jvm.threads.live").asLong());
        applicationResults.setJvmThreadMax(collectionReport.at("/jvm.threads.peak").asLong());


        var deltaUnLoaded = 0l;
        var currentUnLoaded = collectionReport.at("/jvm.classes.unloaded").asLong();
        if (lastClassesUnLoaded.containsKey(containerId)) {
            deltaUnLoaded = currentUnLoaded - lastClassesUnLoaded.get(containerId);
        }
        applicationResults.setJvmClassesUnLoaded(deltaUnLoaded);
        lastClassesUnLoaded.put(containerId, currentUnLoaded);

        var deltaClassesLoaded = 0l;
        var currentClassesLoaded = collectionReport.at("/jvm.classes.loaded").asLong();
        if (lastClassesLoaded.containsKey(containerId)) {
            deltaClassesLoaded = currentClassesLoaded - lastClassesLoaded.get(containerId);
        }
        applicationResults.setJvmClassesLoaded(deltaClassesLoaded);
        lastClassesLoaded.put(containerId, currentClassesLoaded);
        applicationResults.setJvmTotalClassesLoaded(currentClassesLoaded);

        var currentOpenFiles = collectionReport.at("/process.files.open").asLong();
        applicationResults.setOpenFiles(currentOpenFiles);

        var deltaOpenFiles = 0l;
        if (lastOpenFiles.containsKey(containerId)) {
            deltaOpenFiles = currentOpenFiles - lastOpenFiles.get(containerId);
        }

        lastOpenFiles.put(containerId, currentOpenFiles);

        applicationResults.setDeltaOpenFiles(deltaOpenFiles);

        // Memory: jvm.memory.max.mb is incremented multiple times by csapmicrometer:
        // using jvm.gc.max.data.size.mb

        var heapUsed = collectionReport.at("/csap.heap.jvm.memory.used.mb").asLong(collectionReport.at(
                "/jvm.memory.used.mb").asLong());
        applicationResults.setHeapUsed(heapUsed);
        var heapMax = collectionReport.at("/csap.heap.jvm.memory.max.mb").asLong(collectionReport.at(
                "/jvm.gc.max.data.size.mb").asLong());
        applicationResults.setHeapMax(heapMax);

        //
        // GC - use csap aggregate value if available, else use
        //
        var minorGcPath = "/jvm.gc.pause[action=end of minor GC,cause=G1 Evacuation Pause]/total-ms";

        var minorPauseTotal = collectionReport.at(minorGcPath).asLong(0);
        long deltaMinorPause = javaDelta(containerId + "deltaMinorPause", minorPauseTotal);

        // pause time ~= collection time....
//		applicationResults.setNewGcPause( deltaMinorPause ) ;

        var csapMinorAggregatePath = "/csap.jvm.gc.pause.minor/total-ms";
//		var primaryMinor = collectionReport.at( csapMinorAggregatePath ).asLong( 0 ) ;

//		if ( containerId.contains( "agent" ) ) {
//
//			logger.info( "{} defaultMinor: {}, primaryMinor: {}", containerId, minorPauseTotal, primaryMinor ) ;
//
//		}

        long minorGcTotal = collectionReport.at(csapMinorAggregatePath).asLong(minorPauseTotal);
        long deltaMinorGc = javaDelta(containerId + "deltaMinorGc", minorGcTotal);
        applicationResults.setMinorGcInMs(deltaMinorGc);

        var majorGcPath = "/jvm.gc.pause[action=end of major GC,cause=G1 Evacuation Pause]/total-ms";

        var majorPauseTotal = collectionReport.at(majorGcPath).asLong(0);
        long deltaMajorPause = javaDelta(containerId + "deltaMajorPause", majorPauseTotal);
//		applicationResults.setOldGcPause( deltaMajorPause ) ;

        var csapMajorAggregatePath = "/csap.jvm.gc.pause.major/total-ms";
        long majorGcTotal = collectionReport.at(csapMajorAggregatePath)
                .asLong(collectionReport.at(majorGcPath).asLong(0));
        long deltaMajorGc = javaDelta(containerId + "deltaMajorGc", majorGcTotal);
        applicationResults.setMajorGcInMs(deltaMajorGc);

        //
        // Tomcat only
        //
        if (isTomcat) {

            // Sessions
            applicationResults.setSessionsActive(collectionReport.at("/tomcat.sessions.active.current").asLong());

            long deltaSessions = javaDelta(containerId + "deltaSessions",
                    collectionReport.at("/tomcat.sessions.created").asLong());
            applicationResults.setSessionsCount(deltaSessions);

            // tomcat threads
            applicationResults.setTomcatThreadsBusy(collectionReport.at("/tomcat.threads.busy").asLong());
            applicationResults.setTomcatThreadCount(collectionReport.at("/tomcat.threads.current").asLong());

            // overload: http is not collection this: instead
            applicationResults.setHttpConn(collectionReport.at("/tomcat.threads.busy").asLong());

            // http
            long deltaHttpRequests = javaDelta(containerId + "deltaHttpRequests",
                    collectionReport.at("/tomcat.global.request/count").asLong());
            applicationResults.setHttpRequestCount(deltaHttpRequests);

            long deltaHttpTime = javaDelta(containerId + "deltaHttpTime",
                    collectionReport.at("/tomcat.global.request/total-ms").asLong());
            applicationResults.setHttpProcessingTime(deltaHttpTime);

            // overload: http is not collection this: instead
            // double messagesPerSecond = ((double)deltaHttpRequests/deltaHttpTime)*1000;
            // applicationResults.setHttpConn( Math.round(CSAP.roundIt( messagesPerSecond, 2
            // )) ) ;

            long deltaSent = javaDelta(containerId + "deltaSent",
                    collectionReport.at("/tomcat.global.sent.mb").asLong() * 1024);
            applicationResults.setHttpBytesSent(deltaSent);

            long deltaReceived = javaDelta(containerId + "deltaReceived",
                    collectionReport.at("/tomcat.global.received").asLong());
            applicationResults.setHttpBytesReceived(deltaReceived);

        }

        applicationResults.add_results_to_java_collection(serviceCollector.getServiceToJavaMetrics(), containerId);

    }

    private long javaDelta(
            String key,
            long collectedMetricAsLong
    ) {

        // logger.debug( "Servicekey: {} , collectedMetricAsLong: {}", key,
        // collectedMetricAsLong ) ;

        long last = collectedMetricAsLong;

        if (deltaLastCollected.has(key)) {

            collectedMetricAsLong = collectedMetricAsLong - deltaLastCollected.get(key).asLong();

            if (collectedMetricAsLong < 0) {

                collectedMetricAsLong = 0;

            }

        } else {

            collectedMetricAsLong = 0;

            if (csapApis.application().isRunningOnDesktop()) {

                last = 100;

            }

        }

        deltaLastCollected.put(key, last);

        return collectedMetricAsLong;

    }

    private boolean printLocalWarning = true;

    private ResponseEntity<String> perform_collection(
            ServiceInstance serviceInstance,
            String httpCollectionUrlRequested,
            ObjectNode httpConfig,
            ContainerState container
    )
            throws IOException {

        logger.debug("httpCollectionUrl: {}", httpCollectionUrlRequested);

        var httpCollectionUrl = serviceInstance.resolveRuntimeVariables(httpCollectionUrlRequested);

        if (serviceInstance.is_cluster_kubernetes() && httpCollectionUrl.contains(CsapConstants.K8_POD_IP)) {

            httpCollectionUrl = httpCollectionUrl.replaceAll(
                    Matcher.quoteReplacement(CsapConstants.K8_POD_IP),
                    container.getPodIp());

        }

        var desktopTest = false;

        if (csapApis.application().isDesktopProfileActiveOrSpringNull()
                && httpCollectionUrl.contains("localhost")
                && (csapApis.application().isJunit()
                || !serviceInstance.getName().equals(CsapConstants.AGENT_NAME))) {

            var testHostOveride = ServiceCollector.TEST_HOST;

            if (printLocalWarning) {

                logger.warn(CsapApplication.smallTestHeader(
                                "Switching {} localhost to: {}"),
                        serviceInstance.getName(),
                        testHostOveride);
                printLocalWarning = false;

            }

            desktopTest = true;

            httpCollectionUrl = httpCollectionUrl.replaceAll(
                    Matcher.quoteReplacement("localhost.csap.org:7011/api"),
                    Matcher.quoteReplacement(testHostOveride + ":8011/api"));

            httpCollectionUrl = httpCollectionUrl.replaceAll(
                    Matcher.quoteReplacement("localhost.csap.org"),
                    Matcher.quoteReplacement(testHostOveride));

            httpCollectionUrl = httpCollectionUrl.replaceAll(
                    Matcher.quoteReplacement("localhost." + CsapConstants.DEFAULT_DOMAIN),
                    Matcher.quoteReplacement(testHostOveride));

            if (httpCollectionUrl.contains("server-status")) {

                httpCollectionUrl = "http://" + testHostOveride + ":8011/service/httpd?auto";

            }

        }

        var user = httpConfig.path("user");
        var pass = httpConfig.path("pass");

        if (httpConfig.has(csapApis.application().getCsapHostEnvironmentName())) {

            user = httpConfig
                    .path(csapApis.application().getCsapHostEnvironmentName())
                    .path("user");
            pass = httpConfig
                    .path(csapApis.application().getCsapHostEnvironmentName())
                    .path("pass");

        }

        RestTemplate localRestTemplate;

        var currentHostUrl = csapApis.application().getAgentUrl("", "");

        if (httpCollectionUrl.startsWith(currentHostUrl)
                || desktopTest) {

            localRestTemplate = csapApis.application().getAgentPooledConnection(10l,
                    (int) serviceCollector.getMaxCollectionAllowedInMs() / 1000);

        } else {

            localRestTemplate = getRestTemplate(
                    serviceCollector.getMaxCollectionAllowedInMs(),
                    user,
                    pass, serviceInstance.getName() + " collection password");

        }

        ResponseEntity<String> collectionResponse;

        if (httpCollectionUrl.startsWith("file:")) {

            var filePathMinusPrefix = httpCollectionUrl.substring(httpCollectionUrl.indexOf(":") + 1);
//            var stubResults = csapApis.application( ).check_for_stub( "", filePathMinusPrefix );
            var fileResults = "";
            var theFile = new File(filePathMinusPrefix);
            if (Application.isRunningOnDesktop()
                    && !theFile.exists()) {
                theFile = csapApis.application().findTheFileWithStubSupport(filePathMinusPrefix);
            }


            logger.debug("loaded collection from file -  exists: {} path: {} content:\n {}", theFile, theFile.exists(), fileResults);
            if (theFile.exists()) {
                try {
                    if (csapApis.application().isJunit() || logger.isDebugEnabled()) {
                        logger.info("Configuration: {}", httpConfig.toString());
                    }
                    var linesToRead = httpConfig.path("lines-to-read").asInt(1);
                    var lineStartFilter = httpConfig.path("line-start-filter").asText();
                    var wordCountFilter = httpConfig.path("word-count-filter").asInt();
                    fileResults = csapApis.fileUtils()
                            .fileReverseRead(theFile, linesToRead, 10, lineStartFilter, wordCountFilter)
                            .path("content").asText();
                } catch (Exception e) {
                    logger.error("Failed to collect from File: {}", CSAP.buildCsapStack(e));
                    throw e;
                }
            }


            if (csapApis.application().isJunit() || logger.isDebugEnabled()) {
                logger.info(CSAP.buildDescription("loaded collection",
                        "theFile", theFile,
                        "exists", theFile.exists(),
                        "content", fileResults));
            }

            collectionResponse = new ResponseEntity<String>(
                    fileResults,
                    HttpStatus.OK);

        } else if (Application.isRunningOnDesktop() && httpCollectionUrl.startsWith("classpath:")) {
            // File stubResults = new File( getClass()
            // .getResource( httpCollectionUrl.substring(
            // httpCollectionUrl.indexOf( ":" ) + 1 ) )
            // .getFile() );

            var filePathMinusPrefix = httpCollectionUrl.substring(httpCollectionUrl.indexOf(":") + 1);
            var stubResults = csapApis.application().check_for_stub("", filePathMinusPrefix);

            collectionResponse = new ResponseEntity<String>(stubResults,
                    HttpStatus.OK);

        } else {

            if (logger.isDebugEnabled() &&
                    serviceInstance.getName().contains("by-spec")) {

                logger.info("httpCollectionUrl: {} ", httpCollectionUrl);

            }

            logger.debug("Performing collection from: {}", httpCollectionUrl);

            if (csapApis.application().configuration().isDisableRemoteCollection()
                    && httpCollectionUrl.startsWith("http")) {
                throw new RuntimeException(serviceInstance.getName() + ": Remote collection disabled: " + httpCollectionUrl);
            }
            collectionResponse = localRestTemplate.getForEntity(httpCollectionUrl, String.class);
            logger.debug("collectionResponse: {}", collectionResponse);

            if (logger.isDebugEnabled() &&
                    serviceInstance.getName().contains("by-spec")) {

                logger.info("collectionResponse: {} ", collectionResponse);

            }

            // logger.debug("Raw Response: \n{}",
            // collectionResponse.toString());
        }

        return collectionResponse;

    }

    private void processHttpMeterUsingRegex(
            String serviceName,
            ServiceMeter serviceMeter,
            String containerId,
            String patternMatch,
            ServiceCollectionResults applicationResults,
            JsonNode httpConfig,
            String collectionResponse
    ) {

        var matcherSuffix = httpConfig.path("patternMatch").asText();

        var matchedContents = "";

        if (matcherSuffix.equals("byWordIndex")) {

            var lastLine = collectionResponse;
//            if ( lastLine.contains( "\n" )) lastLine = lastLine.substring( lastLine.lastIndexOf( "\n" ) ) ;
            var words = FileApiUtils.words(lastLine);
            logger.debug("{} byWordIndex words: {}", serviceName, words);

            try {

                var wordIndex = Integer.parseInt(serviceMeter.getHttpAttribute());

                if (wordIndex <= words.size()) {
                    matchedContents = words.get(wordIndex - 1);
                } else {
                    logger.warn("{} by wordIndex  {} greater then found size {}", serviceName, serviceMeter.getHttpAttribute(), words.size());
                }

            } catch (Exception e) {

                logger.warn("{} Failed to parse {} using {}", serviceName, serviceMeter.getHttpAttribute(), patternMatch);
                logger.debug("{}", CSAP.buildCsapStack(e));

            }

        } else {

            var valueCollectionPattern = serviceMeter.getRegexPatternForCollector();

            logger.debug("Collecting {} using {} ", serviceName, valueCollectionPattern);

            if (valueCollectionPattern == null) {

                valueCollectionPattern = Pattern.compile(serviceMeter.getHttpAttribute() + matcherSuffix);
                serviceMeter.setRegexPatternForCollector(valueCollectionPattern);

            }

            var regExMatcher = valueCollectionPattern.matcher(collectionResponse);
            var matchesTotal = 0.0;

            while (regExMatcher.find()) {

                var matchedString = regExMatcher.group(1);

                matchesTotal += Double.parseDouble(matchedString);

            }

            matchedContents = Double.toString(matchesTotal);

//			if ( regExMatcher.find( ) ) {
//				
//				// matchedContents = regExMatcher.group( 1 ) ;
//
//			}

        }

        // logger.debug("{} Using match: {}" , collectionResponse,
        // httpConfig.get("patternMatch").asText()) ;
        if (StringUtils.isNotEmpty(matchedContents)) {

            logger.debug("{} matched {}", serviceMeter.getHttpAttribute(), matchedContents);

            try {

                double divideBy = serviceMeter.getDivideBy(serviceCollector.getCollectionIntervalSeconds());
                double multiplyBy = serviceMeter.getMultiplyBy();

                if (serviceMeter.getDecimals() != 0) {

                    Double collectedMetric = Double.parseDouble(matchedContents);

                    double roundedMetric = CSAP.roundIt(collectedMetric * multiplyBy / divideBy, serviceMeter
                            .getDecimals());

                    if (serviceMeter.isDelta()) {

                        roundedMetric = deltaDecimal(serviceMeter, containerId, collectedMetric, divideBy,
                                multiplyBy);

                    }

                    applicationResults.addCustomResultDouble(serviceMeter.getCollectionId(), roundedMetric);

                } else {

                    // default to round
                    Double collectedMetric = Double.parseDouble(matchedContents);
                    long collectedMetricAsLong = Math.round(collectedMetric * multiplyBy / divideBy);
                    long last = collectedMetricAsLong;

                    if (serviceMeter.isDelta()) {

                        String key = containerId
                                + serviceMeter.getCollectionId();

                        if (deltaLastCollected.has(key)) {

                            collectedMetricAsLong = collectedMetricAsLong
                                    - deltaLastCollected
                                    .get(key)
                                    .asLong();

                            if (collectedMetricAsLong < 0) {

                                collectedMetricAsLong = 0;

                            }

                        } else {

                            collectedMetricAsLong = 0;

                        }

                        deltaLastCollected.put(key, last);

                    }

                    applicationResults.addCustomResultLong(serviceMeter.getCollectionId(), collectedMetricAsLong);

                }

            } catch (NumberFormatException e) {

                logger.warn("{} Failed to parse {} using {}", serviceName, serviceMeter.getHttpAttribute(), patternMatch);
                logger.debug("Exception", e);

            }

        } else {

            logger.debug("No match for: " + serviceMeter.getHttpAttribute());
            csapApis.metrics().incrementCounter("collect-http." + serviceName + "." + serviceMeter.getHttpAttribute()
                    + ".failures");

        }

    }

    private void processHttpMeterUsingJson(
            String serviceName,
            ServiceMeter serviceMeter,
            String containerId,
            String patternMatch,
            ServiceCollectionResults applicationResults,
            JsonNode httpConfig,
            JsonNode collectedFromService
    ) {

        // support for JSON
        try {

            var collectedValueAsDouble = -1.0;

            if (serviceMeter.getHttpAttribute().equals("csapHostCpu")) {

                collectedValueAsDouble = csapApis.application().metricManager().getLatestCpuUsage();

            } else if (serviceMeter.getHttpAttribute().startsWith(CSAP_SERVICE_COUNT)) {

                var containerNameToCount = serviceMeter.getHttpAttribute().split(":", 2)[1];

                var dockerContainers = csapApis.osManager().getDockerContainerProcesses();

                if (dockerContainers != null) {

                    collectedValueAsDouble = dockerContainers.stream()
                            .map(container -> container.getMatchName())
                            .filter(containerName -> containerNameToCount.equalsIgnoreCase(containerName))
                            .count();

                }

                logger.debug("containerNameToCount: {}, count: {}", containerNameToCount, collectedValueAsDouble);

                // var instance = csapApis.application().findServiceByNameOnCurrentHost(
                // serviceNameToCount ) ;
//				if ( instance != null ) {
//					collectedValueAsDouble = instance.getContainerStatusList( ).size( ) ;
//				}

            } else if (serviceMeter.getHttpAttribute().equals("csapHostLoad")) {

                collectedValueAsDouble = csapApis.application().metricManager().getLatestCpuLoad();

            } else {

                collectedValueAsDouble = collectedFromService.at(serviceMeter.getHttpAttribute()).asDouble();

            }

            double divideBy = serviceMeter.getDivideBy(serviceCollector.getCollectionIntervalSeconds());
            double multiplyBy = serviceMeter.getMultiplyBy();

            if (serviceMeter.getDecimals() != 0) {

                double roundedMetric = CSAP.roundIt(collectedValueAsDouble * multiplyBy / divideBy, serviceMeter
                        .getDecimals());

                if (serviceMeter.isDelta()) {

                    roundedMetric = deltaDecimal(serviceMeter, containerId, collectedValueAsDouble, divideBy,
                            multiplyBy);

                }

                applicationResults.addCustomResultDouble(serviceMeter.getCollectionId(), roundedMetric);

            } else {

                // default to round
                Double collectedMetric = collectedValueAsDouble;
                long collectedMetricAsLong = Math.round(collectedMetric * multiplyBy / divideBy);
                long last = collectedMetricAsLong;

                if (serviceMeter.isDelta()) {

                    String key = containerId + serviceMeter.getCollectionId();

                    if (deltaLastCollected.has(key)) {

                        collectedMetricAsLong = collectedMetricAsLong
                                - deltaLastCollected
                                .get(key)
                                .asLong();

                        if (collectedMetricAsLong < 0) {

                            collectedMetricAsLong = 0;

                        }

                    } else {

                        collectedMetricAsLong = 0;

                    }

                    deltaLastCollected.put(key, last);

                }

                applicationResults.addCustomResultLong(serviceMeter.getCollectionId(), collectedMetricAsLong);

            }

        } catch (Exception e) {

            csapApis.metrics().incrementCounter("csap.collect-http.failures");
            csapApis.metrics().incrementCounter("collect-http.failures." + serviceName);
            csapApis.metrics().incrementCounter("collect-http.failures." + serviceName + "." + serviceMeter
                    .getCollectionId());
            logger.debug("Skipping attribute: \"" + serviceMeter.getHttpAttribute() + "\" Due to exception: " + e
                    .getMessage());

        }

    }

    private double deltaDecimal(
            ServiceMeter serviceMeter,
            String containerId,
            double collectedValueAsDouble,
            double divideBy,
            double multiplyBy
    ) {

        double roundedMetric = 0.0;
        String deltaStorageKey = containerId + serviceMeter.getCollectionId();
        logger.debug("deltaStorageKey: {}", deltaStorageKey);

        if (deltaLastCollected.has(deltaStorageKey)) {

            var deltaCollected = collectedValueAsDouble
                    - deltaLastCollected
                    .get(deltaStorageKey)
                    .asDouble();
            roundedMetric = CSAP.roundIt(deltaCollected * multiplyBy / divideBy, serviceMeter.getDecimals());

            // restarts need to be reset to 0
            if (roundedMetric < 0) {

                roundedMetric = 0.0;

            }

        }

        deltaLastCollected.put(deltaStorageKey, collectedValueAsDouble);
        return roundedMetric;

    }

    private RestTemplate getRestTemplate(
            long maxConnectionInMs,
            JsonNode user,
            JsonNode pass,
            String desc
    ) {

        logger.debug("maxConnectionInMs: {} , user: {} , Pass: {} ", maxConnectionInMs, user, pass);

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();

        HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultSocketConfig(
                        SocketConfig.custom()
                                .setSoTimeout(Timeout.ofMilliseconds(maxConnectionInMs))
                                .build())
                .build();

        // "user" : "$csapUser1", "pass" : "$csapPass1"
        if (!user.isMissingNode()
                && !pass.isMissingNode()) {

            var credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(null, -1),
                    new UsernamePasswordCredentials(
                            user.asText(),
                            csapApis.application().decode(pass.asText(), desc).toCharArray()));

            HttpClient httpClient = HttpClients
                    .custom()
                    .setConnectionManager(cm)
                    .setDefaultCredentialsProvider(credsProvider)
                    .build();
            factory.setHttpClient(httpClient);

            // factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        }

        factory.setConnectTimeout((int) maxConnectionInMs);
//        factory.setReadTimeout( ( int ) maxConnectionInMs );

        RestTemplate restTemplate = new RestTemplate(factory);

        return restTemplate;

    }
}
