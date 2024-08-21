package org.csap.integations;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.csap.helpers.CSAP;
import org.csap.helpers.CsapApplication;
import org.csap.security.*;
import org.csap.security.config.CsapSecurityProvider;
import org.csap.security.config.CsapSecurityRoles;
import org.csap.security.config.CsapSecuritySettings;
import org.csap.security.oath2.CsapOauth2SecurityConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.provisioning.UserDetailsManagerConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;

import jakarta.inject.Inject;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring Security Integration
 *
 * @author pnightin
 */
@Configuration ( "CsapSecurityConfiguration" )
@ConditionalOnProperty ( CsapSecurityConfiguration.PROPERTIES_ENABLED )
@ConfigurationProperties ( prefix = CsapSecurityConfiguration.PROPERTIES )
@EnableWebSecurity
@Import ( {
        SecurityAutoConfiguration.class,
        CsapSecuritySettings.class,
        CsapSecurityLdapProvider.class,
        CsapSecurityAdProvider.class,
        CsapSecurityController.class,
        CsapSecurityRestFilter.class
} )

//public class CsapSecurityConfiguration extends WebSecurityConfigurerAdapter {
public class CsapSecurityConfiguration {

    final Logger logger = LoggerFactory.getLogger( this.getClass( ) );

    public static final String AUTH_MANAGER_EXPOSE_FOR_REST = "authManagerExposeForRest";
    public static final String PROPERTIES = "csap.security";
    public static final String PROPERTIES_ENABLED = PROPERTIES + ".enabled";

    CsapSecuritySettings csapSecuritySettings;
    CsapSecurityLdapProvider csapSecurityLdapProvider;
    CsapSecurityAdProvider csapSecurityAdProvider;

    @Autowired ( required = false )
    CsapOauth2SecurityConfiguration csapOauth2Configuration;

    @Autowired ( required = false )
    CsapEncryptionConfiguration csapEncrypt;

    @Autowired
    public CsapSecurityConfiguration(
            CsapSecuritySettings settings,
            CsapSecurityLdapProvider csapSecurityLdapProvider,
            CsapSecurityAdProvider csapSecurityAdProvider,
            Environment env
    ) {

        this.csapSecuritySettings = settings;
        this.csapSecurityLdapProvider = csapSecurityLdapProvider;
        this.csapSecurityAdProvider = csapSecurityAdProvider;

        logger.debug( "Starting up: {} , \n\n csap.security.ldap-enabled: {}",
                settings,
                env.getProperty( "csap.security.ldap-enabled" ) );

    }

    public final static String CSAP_CUSTOM_BEAN_NAME = "csapCustomSecurity";

    /**
     * Enable custom http access rules to be implemented. All instance of
     * CustomHttpSecurity will be invoked
     */
    public interface CustomHttpSecurity {

        public void configure(
                HttpSecurity httpSecurity,
                CsapSecuritySettings csapSecuritySettings
        )
                throws Exception;

    }

    // defaults handled by spring boot. ONLY update if boot defaults are not
    // sufficient
    // @see SpringBootWebSecurityConfiguration.DEFAULT_IGNORED
    // private static List<String>
    // CUSTOM_DEFAULT_IGNORED=Arrays.asList("/css/**", "/js/**", "/images/**",
    // "/webjars/**", "/**/favicon.ico") ;
    // @Bean
    // public IgnoredRequestCustomizer getIgnored() {
    // logger.info( "adding public resources: {} ", DEFAULT_IGNORED );
    // IgnoredRequestCustomizer filters = null;
    //
    // filters = ( ignoredRequestConfigurer ) -> {
    // //String[] paths = {"/css", "/images", "/js"};
    // List<RequestMatcher> matchers = new ArrayList<RequestMatcher>();
    // if (!DEFAULT_IGNORED.isEmpty()) {
    // for (String pattern : DEFAULT_IGNORED) {
    // matchers.add(new AntPathRequestMatcher(pattern, null));
    // }
    // }
    // if (!matchers.isEmpty()) {
    // ignoredRequestConfigurer.requestMatchers(new OrRequestMatcher(matchers));
    // }
    // } ;
    // return filters;
    // }

    @Autowired
    ApplicationContext springContext;

    @Autowired
    Environment springEnvironment;

    // in case ignore is needed to completely disable security on
//	@Override
//	public void configure ( WebSecurity web )
//		throws Exception {
//
//		web.ignoring( )
//				.antMatchers( "/css/**" )
//				.antMatchers( "/js/**" )
//				.antMatchers( "/webjars/**" )
//				.antMatchers( "/images/**" )
//				.antMatchers( "/devOps/health" )
//				.antMatchers( "/csap/metrics/**" ) ;
//
//	}
//	@Bean
//	public WebSecurityCustomizer webSecurityCustomizer() {
//
//		return (web) -> web.ignoring().antMatchers(
//				"/css/**",
//				"/js/**",
//				"/webjars/**",
//				"/images/**",
//				"/devOps/health",
//				"/csap/metrics/**"
//		);
//	}

    /**
     * Note the use of spring security fluent apis to concisely define security
     * policy
     * <p>
     * http://spring.io/blog/2013/08/23/spring-security-3-2-0-rc1-highlights-
     * security-headers/
     */

    // https://stackoverflow.com/questions/41588506/spring-security-defaulthttpfirewall-the-requesturi-cannot-contain-encoded-slas
    // @Bean
    // public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
    // DefaultHttpFirewall firewall = new DefaultHttpFirewall();
    // firewall.setAllowUrlEncodedSlash(true);
    // return firewall;
    // }
    //
    // @Override
    // public void configure(WebSecurity web) throws Exception {
    // web.httpFirewall(allowUrlEncodedSlashHttpFirewall());
    // }
    //

//	@Override
//	protected void configure ( final HttpSecurity httpSecurity )
    @Bean
    @Order ( 0 )
    public SecurityFilterChain securityFilterChain( HttpSecurity http ) throws Exception {


        logger.debug( "Running xxx HttpSecurity configure" );

        http
                .authorizeHttpRequests( ( requests ) -> requests
                        .requestMatchers(
                                "/css/**",
                                "/js/**",
                                "/webjars/**",
                                "/images/**",
                                "/devOps/health",
                                "/js/**",
                                "/csap/metrics/**"
                        ).permitAll()


                );


        extendedChain( http );

        return http.build( );
    }

    //	@Bean
//	@Order (1)
    public void extendedChain( HttpSecurity httpSecurity )
            throws Exception {

        logger.debug( "Running csap HttpSecurity configure" );

        if ( csapSecuritySettings.getProvider( ).isLdapEnabled( ) ) {

            httpSecurity
                    .rememberMe( (remember) -> remember
                        .rememberMeServices( csapSecurityLdapProvider.getCsapSingleSignOn( csapSecuritySettings ) )
                    );

        }

        if ( !csapSecuritySettings.isHstsEnabled( ) ) {

            logger.warn( CsapApplication.testHeader(
                    "hsts disabled: https://en.wikipedia.org/wiki/HTTP_Strict_Transport_Security" ) );
            httpSecurity.headers( ).httpStrictTransportSecurity( ).disable( );

        }


        csapSecuritySettings.getRoles( ).checkForInfraDefault( );

        var customRules = springContext.getBeansOfType( CustomHttpSecurity.class );

        if ( customRules.isEmpty( ) ) {

            logger.warn( CsapApplication.header( " using default security policy - it is strongly recommended to add "
                    + "a policy to your service: CsapSecurityConfiguration.CustomHttpSecurity" ) );

            CsapSecurityConfiguration.CustomHttpSecurity mySecurity = ( ( mySec, csapSecuritySettings ) -> {
                mySec
                        // CSRF adds complexity - refer to
                        // https://docs.spring.io/spring-security/site/docs/current/reference/htmlsingle/#csrf
                        // csap.security.csrf also needs to be enabled or this will be ignored
                        .csrf( csrf -> csrf
                                .requireCsrfProtectionMatcher( CsapSecurityConfiguration.buildRequestMatcher( "/login*" ) )
                        )

                        .authorizeHttpRequests( ( requests ) -> requests
                                .requestMatchers(
                                        "/cors/currentTime", "/helloNoSecurity"
                                ).permitAll( )

                                //
                                // test acl failures
                                //
                                .requestMatchers(
                                        "/testAclFailure"
                                ).hasRole( "NonExistGroupToTriggerAuthFailure")

                                //
                                // multi role checks
                                //
                                .requestMatchers(
                                        "/someUrlNeedingAdmin1", "/anotherUrlNeedingAdmin"
                                ).access( ( authenticationSupplier, authorizationContext ) -> {
                                    return csapSecuritySettings.getRoles( )
                                            .check( authenticationSupplier, authorizationContext,
                                                    CsapSecurityRoles.VIEW_ROLE, CsapSecurityRoles.ADMIN_ROLE  );
                                } )

                                //
                                // Everything has VIEW role enabled
                                //
                                .anyRequest( ).access( ( authenticationSupplier, authorizationContext ) -> {
                                    return csapSecuritySettings.getRoles( )
                                            .check( authenticationSupplier, authorizationContext,
                                                    CsapSecurityRoles.VIEW_ROLE );
                                } )

                        );


            } );
            customRules.put( this.getClass( ).getName( ), mySecurity );

        }

        if ( csapSecuritySettings.getMaxConcurrentUserSessions( ) > 0 ) {

            httpSecurity.sessionManagement( )
                    .maximumSessions( csapSecuritySettings.getMaxConcurrentUserSessions( ) )
                    .expiredUrl( "/sessionError" );

        }

        if ( !csapSecuritySettings.isCsrfEnabled( ) ) {

            // cross site security disabled by default due to integration
            // complexity
            // enable using csap.security
            httpSecurity.csrf( ).disable( );

        }

        // @formatter:off
        httpSecurity
                .headers( )
                .cacheControl( ) // no caching on anything; mvcConfig customizes for images
                .and( )
                .frameOptions( )
                .sameOrigin( ) // by default, spring disables html frames. sample has javadoc with frames, so
                // we enable
                .and( )

                .exceptionHandling( )
                .accessDeniedPage( "/accessError" )
        ;

        // String providerType=springEnvironment.getProperty( "csap.security.provider" )
        // ;
        if ( csapSecuritySettings.getProvider( ).getType( ).equals( "oauth2" ) ) {

            logger.debug( "oauth configured: registering oauth services" );
            configureRemoteLoginUsingOauth2( httpSecurity );

        } else {

            logger.info( "Registering local login services" );
            configuringLocalLogin( httpSecurity );

        }

        customRules.values( ).forEach( item -> {

            logger.debug( "Custom security applied: {}", item.getClass( ).getName( ) );

            try {

                item.configure( httpSecurity, csapSecuritySettings );

            } catch ( Throwable t ) {

                logger.error( "Failed to configure: {}, {}",
                        item.getClass( ).getName( ),
                        CSAP.buildCsapStack( t ) );

            }

        } );

        logger.debug( CsapApplication.arrowMessage( "Starting Spring httpSecurity.build" ) );


        // @formatter:on
    }

    // @formatter:off

    private void configuringLocalLogin( HttpSecurity httpSecurity )
            throws Exception {

        logger.debug( "Configuring local logins" );

        httpSecurity

                .formLogin( ( form ) -> form
                        .loginPage( CsapSecurityController.LOGIN_URL ) // Note the form action is the same
                        .passwordParameter( UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_PASSWORD_KEY )
                        .usernameParameter( UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY )
                        .defaultSuccessUrl( "/", false )
                        .failureUrl( "/loginError" )
                        .permitAll( )
                )

                .logout( logout -> logout
                        .logoutRequestMatcher( new AntPathRequestMatcher( "/logout", "GET" ) )
                        .addLogoutHandler( ( request, response, authentication ) -> {

                            logger.debug( "logging user out" );

                            Cookie cookie = new Cookie(
                                    csapSecuritySettings.getCookie( ).getName( ),
                                    null );

                            cookie.setDomain( CustomRememberMeService.getSingleSignOnDomain( request ) );
                            cookie.setPath( "/" );
                            cookie.setMaxAge( 0 );
                            response.addCookie( cookie );

                            try {

                                response.sendRedirect( request.getContextPath( ) );

                            } catch ( Exception e ) {

                                logger.error( "Failed to redirect", e );

                            }

                        } )
                );

    }

    // @formatter:on

    private void configureRemoteLoginUsingOauth2( HttpSecurity httpSecurity )
            throws Exception {

        if ( !csapSecuritySettings.getProvider( ).getOauthLoginPage( ).equals( CsapSecurityProvider.NOT_USED ) ) {

            logger.info( "Enabling oath form login" );
            httpSecurity

                    .formLogin( )
                    .loginPage( csapSecuritySettings.getProvider( ).loginUrl( ) )
                    .defaultSuccessUrl( "/", true )
                    .failureUrl( "/loginError" )
                    .permitAll( )

                    .and( )
                    .oauth2Login( )
                    .loginPage( csapSecuritySettings.getProvider( ).loginUrl( ) )
                    .permitAll( )

            ;

        }

        if ( !csapSecuritySettings.getProvider( ).getOauthUserTokenName( ).equals( CsapSecurityProvider.NOT_USED ) ) {

            csapOauth2Configuration.setOathUserTokenName( csapSecuritySettings.getProvider( )
                    .getOauthUserTokenName( ) );
            csapOauth2Configuration.setOathClientServiceName( csapSecuritySettings.getProvider( )
                    .getOauthClientServiceName( ) );
            csapOauth2Configuration.setOathServiceClaimName( csapSecuritySettings.getProvider( )
                    .getOauthServiceClaimName( ) );

        }

        logger.warn( "migrate oath to spring sec 6") ;

//        httpSecurity
//                .authorizeRequests( ).antMatchers( "/login" ).permitAll( ).and( )
//
//                // .formLogin().loginPage( "/login" ).permitAll().and()
//
//                .oauth2ResourceServer( )
//                .jwt( )
//                .jwtAuthenticationConverter( csapOauth2Configuration.oathServiceAuthoritiesMapper( ) )
//                .and( )
//
//                // https://docs.spring.io/spring-security/site/docs/current/reference/htmlsingle/#oauth2login-advanced-map-authorities
//                .and( )
//                .oauth2Login( )
//
//                // .loginPage( "/login" )
//                // .defaultSuccessUrl( "/", false )
//                // .permitAll()
//
//                .userInfoEndpoint( )
//                // .customUserType( customUserType, WebClientConfig.KEYCLOAK_CLIENT_ROLE )
//                .userAuthoritiesMapper( authorities -> csapOauth2Configuration.oathUserAuthoritiesMapper(
//                        authorities ) )
//                .and( )
//
//                .and( )
//                .oauth2Client( )
//
//                // https://info.michael-simons.eu/2017/12/28/use-keycloak-with-your-spring-boot-2-application/
//                .and( )
//                .logout( )
//                .addLogoutHandler( csapOauth2Configuration.getOpenIdLogoutHandler( ) )
//                .logoutSuccessUrl( "/" );

    }

    /**
     * This is a lifecycle bean - that occurs very early in Spring context.
     */


//	@Bean ( AUTH_MANAGER_EXPOSE_FOR_REST )
//	@ConditionalOnProperty ( "csap.security.rest-api-filter.enabled" )
////	@Override
//	public AuthenticationManager authenticationManagerBean ( )
//		throws Exception {
//
//		return springContext.getBean( AuthenticationManager.class ) ;
//		//return super.authenticationManagerBean( ) ;
//
//	}

//	@Override
//	 protected void configure(AuthenticationManagerBuilder auth) throws Exception {
//	 	auth
//	 	.inMemoryAuthentication().withUser("user").password("password").roles("USER").and()
//	 			.withUser("admin").password("password").roles("USER", "ADMIN");
//	 }
    @Autowired
    public void defineProviders(
            AuthenticationManagerBuilder authenticationBuilder,
            CsapSecuritySettings csapSecuritySettings,
            PasswordEncoder passwordEncoder
    )
            throws Exception {

        logger.info( CsapApplication.header( "type: {}" ), csapSecuritySettings.getProvider( ).getType( ) );

        if ( csapSecuritySettings.getProvider( ).isAdEnabled( ) ) {

            authenticationBuilder.authenticationProvider(
                    csapSecurityAdProvider.activeDirectoryLdapAuthenticationProvider( ) );

        } else if ( csapSecuritySettings.getProvider( ).isLdapEnabled( ) ) {

            csapSecurityLdapProvider.configureLdapProvider( authenticationBuilder,
                    csapSecuritySettings,
                    csapSecuritySettings.getProvider( ).getLdapBindAttributes( ) );

        } else if ( csapSecuritySettings.getProvider( ).isOauth2Enabled( ) ) {

            // optional in memory users for backup logins
            if ( csapSecuritySettings.getProvider( ).isOathLocalLoginSpecified( ) ) {

                logger.debug( "OAuth2 has local users configured" );
                configureInMemoryUsers( authenticationBuilder, passwordEncoder );

            } else {

                logger.warn( "OAuth2 has NO local users configured" );
                authenticationBuilder.inMemoryAuthentication( );

            }

        } else {

            configureInMemoryUsers( authenticationBuilder, passwordEncoder );

        }

        logger.debug( CsapApplication.header( "Completed : {}" ), csapSecuritySettings.getProvider( ).getType( ) );

    }

    /**
     * A rare scenario - useful for running without LDAP security. This enables same
     * file to be used everywhere
     *
     * @param springAuthenticationBuilder
     * @throws IOException
     * @throws Exception
     */
    private void configureInMemoryUsers(
            AuthenticationManagerBuilder springAuthenticationBuilder,
            PasswordEncoder passwordEncoder
    )
            throws IOException,
            Exception {

        List<String> userLines = csapSecuritySettings.getProvider( ).getMemoryUsers( );
        // List<String> userLines = FileUtils.readLines( inMemoryUsers );

        StringBuilder users = new StringBuilder( "Using in memory auth: "
                + userLines );

        try {

            for ( String line : userLines ) {

                String[] columns = line.split( "," );

                if ( columns.length >= 3 &&
                        columns[ 0 ].trim( ).length( ) != 0
                        && !columns[ 0 ].trim( ).startsWith( "#" ) ) {

                    // InMemory is only used for desktop testing
                    UserDetailsManagerConfigurer<?, ?>.UserDetailsBuilder builder = springAuthenticationBuilder
                            .inMemoryAuthentication( )
                            .passwordEncoder( passwordEncoder )
                            .withUser( columns[ 0 ] );

                    String password = csapEncrypt.decodeIfPossible( columns[ 1 ], logger );
                    builder.password( passwordEncoder.encode( password ) );
                    builder.roles( Arrays.copyOfRange( columns, 2, columns.length ) );
                    users.append( "\n" + Arrays.asList( columns ) );

                }

            }

        } catch ( Exception e ) {

            logger.error( CsapApplication.header( "Failed creating CSAP in memory provider: {}" ), CSAP.buildCsapStack(
                    e ) );
            throw e;

        }

        logger.debug( "configured users: {}", users );

    }

    static public RequestMatcher buildRequestMatcher( String... csrfUrlPatterns ) {

        // Enabled CSFR protection on the following urls:
        // new AntPathRequestMatcher( "/**/verify" ),
        // new AntPathRequestMatcher( "/**/login*" ),

        ArrayList<AntPathRequestMatcher> antPathList = new ArrayList<>( );

        for ( String pattern : csrfUrlPatterns ) {

            antPathList.add( new AntPathRequestMatcher( pattern ) );

        }

        RequestMatcher csrfRequestMatcher = new RequestMatcher( ) {

            ArrayList<AntPathRequestMatcher> requestMatchers = antPathList;

            @Override
            public boolean matches( HttpServletRequest request ) {
                // If the request match one url the CSFR protection will be
                // enabled

                if ( request.getMethod( ).equals( "GET" ) ) {

                    return false;

                }

                for ( AntPathRequestMatcher rm : requestMatchers ) {

                    if ( rm.matches( request ) ) {

                        return true;

                    }

                }

                return false;

            } // method matches

        };

        return csrfRequestMatcher;

    }

    // http://blog.codeleak.pl/2016/05/thymeleaf-3-get-started-quickly-with.html
    @Bean
    public SpringSecurityDialect springSecurityDialect( ) {

        return new SpringSecurityDialect( );

    }

    @Inject
    CsapInformation csapInfo;

    public String toString( ) {

        logger.debug( "Constructing info bean for console logs " );

        StringBuilder builder = new StringBuilder( );

        builder.append(
                CSAP.buildDescription( "\n csap.security",

                        "", "",
                        "csrf-enabled", csapSecuritySettings.isCsrfEnabled( )
                                + "  ( https://en.wikipedia.org/wiki/Cross-site_request_forgery )",
                        "hsts-enabled", csapSecuritySettings.isHstsEnabled( )
                                + "  ( https://en.wikipedia.org/wiki/HTTP_Strict_Transport_Security )",
                        "max-concurrent-user-sessions", csapSecuritySettings.getMaxConcurrentUserSessions( )
                                + "     (concurrent, 0 is unlimited)",
                        "", "",

                        "cookie.name", csapSecuritySettings.getCookie( ).getName( ),
                        "cookie.encrypt", "MASKED",
                        "cookie.expire", csapSecuritySettings.getCookie( ).getExpireSeconds( ) + "    (seconds)",
                        "", "",

                        "role.infra", csapSecuritySettings.getRoles( ).getInfraGroup( ),
                        "role.admin", csapSecuritySettings.getRoles( ).getAdminGroup( ),
                        "role.build", csapSecuritySettings.getRoles( ).getBuildGroup( ),
                        "role.view", csapSecuritySettings.getRoles( ).getViewGroup( ),
                        "role.superUsers", csapSecuritySettings.getRoles( ).getSuperUsers( ),
                        "", "",
                        "provider", csapSecuritySettings.getProvider( ).getType( ),
                        "", ""

                ) );

        if ( csapSecuritySettings.getProvider( ).isLdapEnabled( ) ) {

            var dirUserDesc = csapSecuritySettings.getProvider( ).getDirectoryUser( );

            if ( StringUtils.isEmpty( dirUserDesc ) ) {

                dirUserDesc = "Not specified - LDAP Bind Authentication will be used";

            }

            builder.append(
                    CSAP.buildDescription( "",
                            "provider.url", csapSecuritySettings.getProvider( ).getUrl( ) + " (ldap)",
                            "directory-dn", csapSecuritySettings.getProvider( ).getDirectoryDn( ),
                            "directory-dn-generic", csapSecuritySettings.getProvider( ).getDirectoryDnGeneric( ),
                            "search-user", csapSecuritySettings.getProvider( ).getSearchUser( ),
                            "search-groups", csapSecuritySettings.getProvider( ).getSearchGroups( ),
                            "search-group-filter", csapSecuritySettings.getProvider( ).getSearchGroupFilter( ),
                            "directory-user", dirUserDesc,
                            "directory-password", csapSecuritySettings.getProvider( ).getDirectoryPassword( ).length( )
                                    + " * MASKED",
                            "", ""

                    ) );

            var formattedAttributes = csapSecuritySettings.getProvider( ).getLdapBindAttributes( ).stream( )
                    .map( CSAP::pad )
                    .collect( Collectors.joining( ) );
            builder.append( CSAP.padLine( "User Attributes Retrieved" ) + "\n\t\t" + WordUtils.wrap(
                    formattedAttributes, 80, "\n\t\t",
                    false ) );

        } else if ( csapSecuritySettings.getProvider( ).isAdEnabled( ) ) {

            var dirUserDesc = csapSecuritySettings.getProvider( ).getDirectoryUser( );

            if ( StringUtils.isEmpty( dirUserDesc ) ) {

                dirUserDesc = "Not specified - LDAP Bind Authentication will be used";

            }

            builder.append(
                    CSAP.buildDescription( "",
                            "provider.url", csapSecuritySettings.getProvider( ).getUrl( ) + "   (Active Directory)",
                            "directory-domain", csapSecuritySettings.getProvider( ).getDomain( ),
                            "directory-dn", csapSecuritySettings.getProvider( ).getDirectoryDn( ),
                            "directory-dn-generic", csapSecuritySettings.getProvider( ).getDirectoryDnGeneric( ),
                            "search-user", csapSecuritySettings.getProvider( ).getSearchUser( ),
                            "search-groups", csapSecuritySettings.getProvider( ).getSearchGroups( ),
                            "search-group-filter", csapSecuritySettings.getProvider( ).getSearchGroupFilter( ),
                            "directory-user", dirUserDesc,
                            "directory-password", csapSecuritySettings.getProvider( ).getDirectoryPassword( ).length( )
                                    + " * MASKED",
                            "", ""

                    ) );

        } else if ( csapSecuritySettings.getProvider( ).isOauth2Enabled( ) ) {

            builder.append( CSAP.padLine( "OAuth Login page" ) + csapSecuritySettings.getProvider( )
                    .getOauthLoginPage( ) );
            builder.append( CSAP.padLine( "OAuth Local Login" ) + csapSecuritySettings.getProvider( )
                    .getOauthLoginLocal( ) );
            builder.append( CSAP.padLine( "OAuth User Token" ) + csapSecuritySettings.getProvider( )
                    .getOauthUserTokenName( ) );

            if ( csapSecuritySettings.getProvider( ).isOathLocalLoginSpecified( ) ) {

                builder.append( CSAP.padLine( "Local Login" ) + "enabled: " + csapInfo.getFullServiceUrl( )
                        + "/login?local=true" );

            } else {

                builder.append( CSAP.padLine( "Local Login" ) + "disabled" );

            }

            builder.append( csapOauth2Configuration.buildConfigurationMessage( ).toString( ) );

        } else {

            try {

                builder.append( CSAP.padLine( "In Memory Auth loaded from: csap.security.provider.memory-users" ) );
                builder.append( CSAP.padLine( "users found" ) );
                csapSecuritySettings.getProvider( ).getMemoryUsers( ).stream( ).forEach( line -> {

                    builder.append( "\t" + line.split( "," )[ 0 ] );

                } );
                ;

            } catch ( Exception e ) {

                logger.error( "Failed to load roles: {}", CSAP.buildCsapStack( e ) );

            }

        }

        Map<String, CustomHttpSecurity> customRules = springContext.getBeansOfType( CustomHttpSecurity.class );

        if ( !customRules.isEmpty( ) ) {

            logger.debug( "Custom security applied" );
            customRules.values( ).forEach( customRule -> {

                builder.append( "\n" + CSAP.padLine( "Custom Acls" ) + customRule.getClass( ).getName( ) );

            } );

        } else {

            builder.append( "\n" + CSAP.padLine( "Custom Acls" ) + "Not Configured" );

        }

        builder.append( "\n " );

        logger.debug( builder.toString( ) );
        return builder.toString( );

    }

}
