package eu.melodic.event.test;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class PortConfig {

    @Value("${additional.ports:}")
    private String additionalPorts;
    @Value("${https.enabled:false}")
    private boolean httpsEnabled;
    @Value("${https.keystore.file:}")
    private String keyStoreFile;
    @Value("${https.keystore.password:}")
    private String keyStorePassword;
    @Value("${https.truststore.file:}")
    private String trustStoreFile;
    @Value("${https.truststore.password:}")
    private String trustStorePassword;
    @Value("${https.key.alias:}")
    private String keyAlias;

    @Bean
    ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
        Connector[] additionalConnectors = createAdditionalConnectors(additionalPorts);
        if (additionalConnectors!=null && additionalConnectors.length>0) {
            tomcat.addAdditionalTomcatConnectors(additionalConnectors);
        }
        return tomcat;
    }

    private Connector[] createAdditionalConnectors(String portsStr) {
        if (portsStr==null || portsStr.trim().isEmpty()) return null;
        List<Integer> ports =
        Arrays.asList(portsStr.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .filter(i -> i>0)
                .collect(Collectors.toList());
        log.info("createAdditionalConnectors(): ports={}", ports);

        List<Connector> connectorList = ports.stream()
                .map(port -> {
                    if (!httpsEnabled) {
                        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
                        connector.setPort(port);
                        return connector;
                    } else {
                        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
                        Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
//                        File keystore = new ClassPathResource(keyStoreFile).getFile();
//                        File truststore = new ClassPathResource(trustStoreFile).getFile();
                        File keystore = new File(keyStoreFile);
                        File truststore = new File(trustStoreFile);
                        log.info("----> keystore: {}", keystore.getAbsolutePath());
                        log.info("----> truststore: {}", truststore.getAbsolutePath());
                        connector.setScheme("https");
                        connector.setSecure(true);
                        connector.setPort(port);
                        protocol.setSSLEnabled(true);
                        protocol.setKeystoreFile(keystore.getAbsolutePath());
                        protocol.setKeystorePass(keyStorePassword);
                        protocol.setTruststoreFile(truststore.getAbsolutePath());
                        protocol.setTruststorePass(trustStorePassword);
                        protocol.setKeyAlias(keyAlias);

                        return connector;
                    }
                })
                .collect(Collectors.toList());
        log.info("createAdditionalConnectors(): connectors={}", connectorList);
        Connector[] connectors = connectorList.toArray(new Connector[0]);
        return connectors;
    }

    /*protected SSLContext createSslContext(final KeyStore keyStore, String keyStorePassword, final KeyStore trustStore) throws Exception {
        KeyManager[] keyManagers;
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());
        keyManagers = keyManagerFactory.getKeyManagers();

        TrustManager[] trustManagers;
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        trustManagers = trustManagerFactory.getTrustManagers();

        SSLContext sslContext;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);

        return sslContext;
    }*/
}
