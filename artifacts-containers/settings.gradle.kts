
//
// build multi-arch containers ./gradlew buildx
//

//include(":maven")
//include(":govc")
//include(":helm")
//include(":java")
//include(":tomcat")
include(":containers:hello")
include(":containers:sysbench")
include(":containers:fio")
include(":containers:stress")
include(":containers:installer")
include(":containers:test-utils")

//project(":hello").projectDir = file("containers/hello")
//project(":sysbench").projectDir = file("containers/sysbench")
//project(":fio").projectDir = file("containers/fio")
//project(":stress").projectDir = file("containers/stress")
//project(":installer").projectDir = file("containers/installer")
//project(":test-utils").projectDir = file("containers/test-utils")


