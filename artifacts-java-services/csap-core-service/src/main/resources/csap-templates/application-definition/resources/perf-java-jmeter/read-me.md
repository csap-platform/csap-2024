

## Provides
Automated load test of a representative java project:
- tomcat 9 (SpringBoot 2.6.x)
- log4j
- messaging: activemq (embedded or external)
- db: embedded (hsql) or external (postgres, mysql, ...)



## References
-  [Extended Scenario Description](https://confluence.yourcompany.com/display/~peter.nightingale/Tomcat+Reference+App)
-  [BitBucket: Reference Project](https://bitbucket.yourcompany.com/projects/OMS/repos/wcsap/browse/csap-starter-tester)
-  [BitBucket: Reference Project: BurnCpu.java](https://bitbucket.yourcompany.com/projects/OMS/repos/wcsap/browse/csap-starter-tester/src/main/java/org/sample/input/http/ui/windows/HeapTester.java#300)
-  [BitBucket: JMeter Definition](https://bitbucket.yourcompany.com/projects/OMS/repos/wcsap/browse/csap-core-service/src/main/resources/csap-templates/application-definition/resources)

## Test Settings (Via Csap Config Map Parameters)
```bash
jref-package-settings:
  testToRun: 6jvm-port
  testToRunMinUsers: 6jvm-port
  testToRunMaxUsers: default
  runMinutes: 60
  rampSeconds: 120
  queryThreads: 12   # core tomcat queries to html operations: addToDbDB, addToMessaging, etc.
  queryDelayMs: 0    # delay between each thread
  purgeDbThreads: 6  # runs a simpley DB query to delete all data stored during test
  purgeDelayMs: 100  
  burnThreads: 12    # allocates/restructures string to consume ~5s of cpu 
  burnDelayMs: 10000
```  
