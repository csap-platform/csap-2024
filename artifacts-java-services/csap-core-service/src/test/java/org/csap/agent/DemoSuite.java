package org.csap.agent;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Demo Suite")
@SelectPackages( "org.csap.agent.project.loader" )
@IncludeClassNamePatterns(".*")
@IncludeTags("Quick & Easy & ! Long")
public class DemoSuite {
}
