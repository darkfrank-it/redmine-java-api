package com.taskadapter.redmineapi;

import com.taskadapter.redmineapi.internal.Transport;
import com.taskadapter.redmineapi.internal.URIConfigurator;
import com.taskadapter.redmineapi.internal.comm.BaseCommunicator;
import com.taskadapter.redmineapi.internal.comm.Communicator;
import com.taskadapter.redmineapi.internal.comm.redmine.RedmineApiKeyAuthenticator;
import com.taskadapter.redmineapi.internal.comm.redmine.RedmineUserPasswordAuthenticator;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Collection;
import java.util.List;

/**
 * <strong>Entry point</strong> for the API. Use this class to communicate with Redmine servers.
 * <p>
 * Collection of creation methods for the redmine. Method number may grow as
 * grows number of requirements. However, having all creation methods in one
 * place allows us to refactor RemineManager internals without changing this
 * external APIs. Moreover, we can create "named constructor" for redmine
 * instances. This will allow us to have many construction methods with the same
 * signature.
 * <p>
 * Sample usage:
 * <pre>
 * RedmineManager redmineManager = RedmineManagerFactory.createWithUserAuth(redmineURI, login, password);
 * </pre>
 *
 * @see RedmineManager
 */
public final class RedmineManagerFactory {
    private static final String DEFAULT_USER_PASSWORD_AUTHENTICATOR_CHARSET = "UTF-8";

    /**
     * Prevent construction of this object even with use of dirty tricks.
     */
    private RedmineManagerFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a non-authenticating redmine manager.
     *
     * @param uri redmine manager URI.
     */
    public static RedmineManager createUnauthenticated(String uri) {
        return createUnauthenticated(uri, createDefaultHttpClient(uri));
    }

    /**
     * Creates a non-authenticating redmine manager.
     *
     * @param uri        redmine manager URI.
     * @param httpClient you can provide your own pre-configured HttpClient if you want
     *                   to control connection pooling, manage connections eviction, closing, etc.
     */
    public static RedmineManager createUnauthenticated(String uri,
                                                       HttpClient httpClient) {
        return createWithUserAuth(uri, null, null, httpClient);
    }

    /**
     * Creates an instance of RedmineManager class. Host and apiAccessKey are
     * not checked at this moment.
     *
     * @param uri          complete Redmine server web URI, including protocol and port
     *                     number. Example: http://demo.redmine.org:8080
     * @param apiAccessKey Redmine API access key. It is shown on "My Account" /
     *                     "API access key" webpage (check
     *                     <i>http://redmine_server_url/my/account</i> URL). This
     *                     parameter is <strong>optional</strong> (can be set to NULL) for Redmine
     *                     projects, which are "public".
     */
    public static RedmineManager createWithApiKey(String uri,
                                                  String apiAccessKey) {
        return createWithApiKey(uri, apiAccessKey,
                createDefaultHttpClient(uri));
    }

    /**
     * Creates an instance of RedmineManager class. Host and apiAccessKey are
     * not checked at this moment.
     *
     * @param uri          complete Redmine server web URI, including protocol and port
     *                     number. Example: http://demo.redmine.org:8080
     * @param apiAccessKey Redmine API access key. It is shown on "My Account" /
     *                     "API access key" webpage (check
     *                     <i>http://redmine_server_url/my/account</i> URL). This
     *                     parameter is <strong>optional</strong> (can be set to NULL) for Redmine
     *                     projects, which are "public".
     * @param httpClient   Http Client. you can provide your own pre-configured HttpClient if you want
     *                     to control connection pooling, manage connections eviction, closing, etc.
     */
    public static RedmineManager createWithApiKey(String uri,
                                                  String apiAccessKey, HttpClient httpClient) {
        Communicator<HttpResponse> baseCommunicator = new BaseCommunicator(httpClient);

        RedmineApiKeyAuthenticator<HttpResponse> authenticator = new RedmineApiKeyAuthenticator<>(
                baseCommunicator, apiAccessKey);

        return new RedmineManager(
                new Transport(new URIConfigurator(uri), authenticator)
        );
    }

    /**
     * Creates a new RedmineManager with user-based authentication.
     *
     * @param uri      redmine manager URI.
     * @param login    user's name.
     * @param password user's password.
     */
    public static RedmineManager createWithUserAuth(String uri, String login,
                                                    String password) {
        return createWithUserAuth(uri, login, password,
                createDefaultHttpClient(uri));
    }

    /**
     * Creates a new redmine manager with user-based authentication.
     * <p>
     * This method will use UTF-8 when converting login plus password into Base64-encoded blob.
     * Please use another "create" method in this class if you want to use some other encoding.
     * although... why would you? please save the world and use UTF-8 encoding where possible.
     *
     * @param uri        redmine manager URI.
     * @param login      user's name.
     * @param password   user's password.
     * @param httpClient you can provide your own pre-configured HttpClient if you want
     *                   to control connection pooling, manage connections eviction, closing, etc.
     */
    public static RedmineManager createWithUserAuth(String uri, String login,
                                                    String password, HttpClient httpClient) {
        Communicator<HttpResponse> baseCommunicator = new BaseCommunicator(httpClient);

        RedmineUserPasswordAuthenticator<HttpResponse> passwordAuthenticator = new RedmineUserPasswordAuthenticator<>(
                baseCommunicator, DEFAULT_USER_PASSWORD_AUTHENTICATOR_CHARSET, login, password);
        Transport transport = new Transport(
                new URIConfigurator(uri), passwordAuthenticator);
        return new RedmineManager(transport);
    }

    /**
     * Creates a new redmine manager with user-based authentication.
     *
     * @param uri                   redmine manager URI.
     * @param authenticationCharset charset to use when converting login and password to Base64-encoded string
     * @param login                 user's name.
     * @param password              user's password.
     * @param httpClient            you can provide your own pre-configured HttpClient if you want
     *                              to control connection pooling, manage connections eviction, closing, etc.
     */
    public static RedmineManager createWithUserAuth(String uri, String authenticationCharset,
                                                    String login,
                                                    String password, HttpClient httpClient) {
        Communicator<HttpResponse> baseCommunicator = new BaseCommunicator(httpClient);

        RedmineUserPasswordAuthenticator<HttpResponse> passwordAuthenticator = new RedmineUserPasswordAuthenticator<>(
                baseCommunicator, authenticationCharset, login, password);
        Transport transport = new Transport(
                new URIConfigurator(uri), passwordAuthenticator);
        return new RedmineManager(transport);
    }

    /**
     * Creates default insecure connection manager.
     *
     * @return default insecure connection manager.
     * @deprecated Use better key-managed factory with additional keystores.
     */
    @Deprecated
    public static PoolingHttpClientConnectionManager createInsecureConnectionManager() {
        return createConnectionManager((TlsSocketStrategy) ClientTlsStrategyBuilder.create()
                .setSslContext(SSLContexts.createSystemDefault())
                .build());
    }

    /**
     * Creates a connection manager with extended trust relations. It would
     * use both default system trusted certificates as well as all certificates
     * defined in the <code>trustStores</code>.
     *
     * @param trustStores list of additional trust stores to be included in the
     *                    trust chain. The server will be validated against all system-provided
     *                    CAs and all the ones provided via this list.
     * @return connection manager with extended trust relationship.
     */
    public static PoolingHttpClientConnectionManager createConnectionManagerWithExtraTrust(Collection<KeyStore> trustStores) throws KeyManagementException, KeyStoreException {
        return createConnectionManager((TlsSocketStrategy) ClientTlsStrategyBuilder.create()
                .setSslContext(SSLContexts.createSystemDefault())
                .build());
    }

    /**
     * Creates a connection manager with extended trust relations and Client
     * Certificate authentication. It would use both default system trusted
     * certificates as well as all certificates defined in the
     * <code>trustStores</code>.
     *
     * @param keyStore         key store containing the client certificate to use.
     * @param keyStorePassword the keyStore password string.
     * @param trustStores      list of additional trust stores to be included in the
     *                         trust chain. The server will be validated against all system-provided
     *                         CAs and all the ones provided via this list.
     * @return connection manager with extended trust relationship.
     * @throws KeyManagementException
     * @throws KeyStoreException
     */
    public static PoolingHttpClientConnectionManager createConnectionManagerWithClientCertificate(KeyStore keyStore, String keyStorePassword, Collection<KeyStore> trustStores) throws KeyManagementException, KeyStoreException {
        return createConnectionManager((TlsSocketStrategy) ClientTlsStrategyBuilder.create()
                .setSslContext(SSLContexts.createSystemDefault())
                .build());
    }

    /**
     * Creates default connection manager.
     */
    public static PoolingHttpClientConnectionManager createDefaultConnectionManager() {
        return createConnectionManager((TlsSocketStrategy) ClientTlsStrategyBuilder.create()
                .setSslContext(SSLContexts.createSystemDefault())
                .build());
    }

    /**
     * Creates system default connection manager. Takes in account system
     * properties for SSL configuration.
     *
     * @return default insecure connection manager.
     */
    public static PoolingHttpClientConnectionManager createSystemDefaultConnectionManager() {
        return createConnectionManager((TlsSocketStrategy) ClientTlsStrategyBuilder.create()
                .setSslContext(SSLContexts.createSystemDefault())
                .build());
    }

    public static PoolingHttpClientConnectionManager createConnectionManager(TlsSocketStrategy tlsSocketStrategy) {

        return PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(tlsSocketStrategy)
//                .setTlsSocketStrategy((TlsSocketStrategy) ClientTlsStrategyBuilder.create()
//                        .setSslContext(SSLContexts.createSystemDefault())
//                        .setTlsVersions(TLS.V_1_3)
//                        .build())
//                .setDefaultSocketConfig(SocketConfig.custom()
//                        .setSoTimeout(Timeout.ofMinutes(1))
//                        .build())
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                .setConnPoolPolicy(PoolReusePolicy.LIFO)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setSocketTimeout(Timeout.ofMinutes(1))
                        .setConnectTimeout(Timeout.ofMinutes(1))
                        .setTimeToLive(TimeValue.ofMinutes(10))
                        .build())
                .build();
    }


    public static HttpClient createDefaultHttpClient(String uri) {
        try {
            return getNewHttpClient(uri, createSystemDefaultConnectionManager());
        } catch (Exception e) {
            e.printStackTrace();
            return HttpClients.createDefault();
        }
    }

    /**
     * Helper method to create an http client from connection manager. This new
     * client is configured to use system proxy (if any).
     */
    public static CloseableHttpClient getNewHttpClient(String uri, PoolingHttpClientConnectionManager connectionManager) {

        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        HttpHost proxy = configureProxy(uri);
        if (proxy != null) {
            httpClientBuilder.setProxy(proxy);
            CredentialsProvider defaultCredentialsProvider = configureProxyCredential();
            httpClientBuilder.setDefaultCredentialsProvider(defaultCredentialsProvider);
        }

        return httpClientBuilder.build();
    }

    private static HttpHost configureProxy(String uri) {
        String proxyHost = System.getProperty("http.proxyHost");
        String proxyPort = System.getProperty("http.proxyPort");
        if (proxyHost != null && proxyPort != null) {

            //check standard java nonProxyHost
            List<Proxy> proxyList = null;
            try {
                proxyList = ProxySelector.getDefault().select(new URI(uri));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            if (proxyList != null && proxyList.get(0) == Proxy.NO_PROXY) {
                //use no proxy for this host
                return null;
            }

            int port;
            try {
                port = Integer.parseInt(proxyPort);
            } catch (NumberFormatException e) {
                throw new RedmineConfigurationException("Illegal proxy port "
                        + proxyPort, e);
            }
            return new HttpHost(proxyHost, port);
        }
        return null;
    }

    private static CredentialsProvider configureProxyCredential() {
        String proxyHost = System.getProperty("http.proxyHost");
        String proxyPort = System.getProperty("http.proxyPort");
        if (proxyHost != null && proxyPort != null) {
            int port;
            try {
                port = Integer.parseInt(proxyPort);
            } catch (NumberFormatException e) {
                throw new RedmineConfigurationException("Illegal proxy port "
                        + proxyPort, e);
            }
            String proxyUser = System.getProperty("http.proxyUser");
            if (proxyUser != null) {
                String proxyPassword = System.getProperty("http.proxyPassword");
                return CredentialsProviderBuilder.create().add(new AuthScope(proxyHost, port),
                        new UsernamePasswordCredentials(proxyUser,
                                proxyPassword.toCharArray())).build();

            }
        }
        return null;
    }
}
