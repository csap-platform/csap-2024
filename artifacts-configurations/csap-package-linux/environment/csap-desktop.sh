#!/bin/sh

csapDeployAbort="CSAP_DEPLOY_ABORT"

#echo $0
csapSourceFolder=$(dirname $0)
if test -z "$CSAP_FOLDER" ; then
  CSAP_FOLDER=$HOME/csap ;
  #echo -e "\n\n CSAP_FOLDER: '$CSAP_FOLDER' \n\n"
  if ! test -r $CSAP_FOLDER/bin ; then

    echo -e "\n\n create csap working folder for script helpers \n\n"
    mkdir --parents $CSAP_FOLDER/bin
    ln -s $0 $CSAP_FOLDER/bin/csap-environment.sh
    ln -s $csapSourceFolder/functions $CSAP_FOLDER/bin/functions
  fi ;
fi


#
# Note: csap-install and remote-install both customize
#
ENV_FUNCTIONS=${ENV_FUNCTIONS:-$CSAP_FOLDER/bin/functions} ;
source $ENV_FUNCTIONS/misc.sh
source $ENV_FUNCTIONS/print.sh
source $ENV_FUNCTIONS/container.sh
source $ENV_FUNCTIONS/network.sh
source $ENV_FUNCTIONS/service.sh


alias zmc="$HOME/azul/zmc8.3.1.81-ca-macos_x64/Azul\ Mission\ Control.app/Contents/MacOS/zmc"

alias cpuInfo="sysctl -a | grep machdep.cpu"



#alias grep='grep --color=auto --exclude-dir={.bzr,CVS,.git,.hg,.svn,.idea,.tox}'
#alias ff="clear && printf '\e[3J';"
#alias ff="clear"
function tt() {
#  clear && printf '\e[3J';
  clear
}
#
# alias sesh="sesh init eng"

#
# my aliases
#
#alias theme='(){ export ZSH_THEME="$@" && source $ZSH/oh-my-zsh.sh }'
function theme() {
  export ZSH_THEME="$@" && source $ZSH/oh-my-zsh.sh
}

#
# multtail depends on ~/.multitailrc
#
alias mtail="multitail -cS zoo"

#
#  clean up intellij when IDE fails to successfully load the project
#
function ijClean() {

  local rootIntellijFolder=${1-.} l

  print_section "Deleting build, .gradle, and .idea files to trigger a clean import"

  if ! test -d $rootIntellijFolder/.idea ; then
    if ! confirmation_prompt "WARNING Intellij Folder not found: $rootIntellijFolder/.idea" ; then
      print_subsection "Exiting"
      return 1;
    fi
  fi ;


  print_and_run find $rootIntellijFolder -type d -name build -prune -exec 'rm -rf "{}" \;'

  print_and_run find $rootIntellijFolder -type d -name ".gradle" -prune -exec 'rm -rf "{}" \;'

  print_and_run find $rootIntellijFolder -type d -name ".idea" -prune -exec 'rm -rf "{}" \;'

}

alias ij="cd ~/IdeaProjects"


function stressGradle() {

  local iterationCount=${1:-1}
  local isVerbose=${2:-false}


  silentCleanGradle
  print_section "$stressGradle $iterationCount : $(pwd)"


  setTimer
  gradle_goodness gradle - -version | addTimerDescription "gradle version and start"

  for runNumber in $(seq 1 $iterationCount);  do

      if (( $runNumber > 1 )) ; then
        sleep 1
      fi
      print_subsection "Run $runNumber"
      if $isVerbose ; then
        _stressGradle
      else
        _stressGradle | grep --regexp "real" --regexp "buildError" --regexp "(verification)"
      fi

    done
}

function _stressGradle() {
  local showFullOutput=${1:-false}
  local testTarget=${2:-.}
  local baseFile="artifacts-java-libs/csap-starter/src/main/java/org/csap/integations/CsapBootConfig.java"
  if  test -d artifacts-java-libs/csap-starter ; then
   testTarget="."
   baseFile="artifacts-java-libs/csap-starter/src/main/java/org/csap/integations/CsapBootConfig.java"
  fi

  print_two_columns "showFullOutput" "$showFullOutput"
  print_two_columns "testTarget" "$testTarget"
#  print_two_columns "testTarget2" "$testTarget2"

  #gradle_goodness build $testTarget   | addTimerDescription "gradle --rerun-tasks $testTarget test"


  #
  # scenarios
  #
  _stressDayInTheLife $testTarget $baseFile
#  _stressCompile $testTarget $baseFile
#  _stressTest $testTarget $baseFile
#  _stressJar $testTarget $baseFile
#  _leakFinder $testTarget $baseFile

}

function _stressCompile() {
  local testTarget=${1:-}
  local baseFile=${2:-}

  local classFile=$( basename ${baseFile%.java}.class )

  print_two_columns "classFile" "$classFile"
  print_two_columns "testTarget" "$testTarget"

  mv --verbose $baseFile ${baseFile}Broke.java

  gradle_goodness gradle "$testTarget" classes  | addTimerDescription "Compile Fail: gradle $testTarget classes"
  print_subsection2 "(verification) classes found: $(find $testTarget -name "$classFile")"

  mv --verbose ${baseFile}Broke.java $baseFile

  gradle_goodness gradle "$testTarget" classes | addTimerDescription "Compile Pass: gradle $testTarget classes"

  print_subsection2 "(verification) classes found: $(basename $(find $testTarget -name "$classFile")) "
}

function _stressTest() {
  local testTarget=${1:-}
  local baseFile=${2:-}

  gradle_goodness gradle "$testTarget" test | addTimerDescription "Testcase: gradle $testTarget test"
}



function _stressJar() {
  local testTarget=${1:-}
  local baseFile=${2:-}

  # --rerun (jar task) vs. --rerun-tasks (all dependent tasks)
  gradle_goodness default "$testTarget" clean jar --rerun-tasks | addTimerDescription "Testcase: gradle $testTarget jar --rerun-tasks"
  print_subsection2 "(verification)  jars found: $(find $testTarget -name '*.jar' | wc -l)"

}


function _leakFinder() {
  local testTarget=${1:-}
  local baseFile=${2:-}

  # --rerun (jar task) vs. --rerun-tasks (all dependent tasks)
  #gradle_goodness default "$testTarget" processResources --rerun-tasks | addTimerDescription "Testcase: gradle $testTarget jar --rerun-tasks"
  gradle_goodness default - processResources --rerun-tasks | addTimerDescription "Testcase: gradle processResources --rerun-tasks"
  #print_subsection2 "(verification)  jars found: $(find -name '*.jar' | wc -l)"


}

function _stressDayInTheLife() {
  local testTarget=${1:-}
  local baseFile=${2:-}

#  mv --verbose $baseFile ${baseFile}Broke.java
#  gradle_goodness gradle "$testTarget" test  | addTimerDescription "DayInTheLife Compile Fail: gradle $testTarget test"
#
#  mv --verbose ${baseFile}Broke.java $baseFile
#  gradle_goodness gradle "$testTarget" test | addTimerDescription "DayInTheLife Test Pass: gradle $testTarget test"


  _restoreFile $baseFile
  add_after_match_in_file "traceCalculationInvoked" "Stringg failForCompileTest=\"$(date)\";" $baseFile
  gradle_goodness gradle "$testTarget" classes | addTimerDescription "DayInTheLife Compile Fail: gradle $testTarget classes"

  _restoreFile $baseFile
  add_after_match_in_file "traceCalculationInvoked" "String passForCompile=\"$(date)\";" $baseFile
  gradle_goodness gradle "$testTarget" classes | addTimerDescription "DayInTheLife Compile Pass: gradle $testTarget classes"
  gradle_goodness gradle "$testTarget" test | addTimerDescription "DayInTheLife Test Pass: gradle $testTarget test"
  gradle_goodness gradle "$testTarget" jar | addTimerDescription "DayInTheLife jar Pass: gradle $testTarget jar"

  print_subsection2 "(verification)  $testTarget jars found: $(find "$testTarget" -name '*.jar' | wc -l)"

  _restoreFile $baseFile
}

function _restoreFile() {

  local baseFile=${1:-}

    git restore $baseFile
}


function gradleBench() {
  local iterationCount=${1:-1}
  local isVerbose=${2:-false}

  for runNumber in $(seq 1 $iterationCount);  do

    print_subsection "Run $runNumber"
    if $isVerbose ; then
      csapgBenchmark
    else
      csapgBenchmark | grep --regexp "real" --regexp "buildError"
    fi
  done
}

function csapgBenchmark() {
  local showFullOutput=${1:-false}
  local testTarget=${2:-path/to/first}
  local testTarget2=${3:-path/to/second}

  print_two_columns "showFullOutput" "$showFullOutput"
  print_two_columns "testTarget" "$testTarget"
  print_two_columns "testTarget2" "$testTarget2"

  setTimer
  silentCleanGradle


  gradle_goodness switch new
  silentCleanGradle
  print_super " STANDALONE Benchmark $testTarget"

#  set -o pipefail
  gradle_goodness gradle - -version | addTimerDescription "gradle version and start"
  print_on_error ${pipestatus[1]} -eq 0 "buildError: gradle - -version"

  echo 'PRERUN cleanup: pkill -9 java ;  pgrep -lf gradle ; pgrep -lf java'
  pkill -9 java 2>&1 >/dev/null ; sleep 2;  pgrep -lf gradle ; pgrep -lf java

  gradle_goodness gradle clean | addTimerDescription "gradle clean"
  print_on_error ${pipestatus[1]} "buildError: gradle clean"

  if [[ $showFullOutput == "yes" ]] ; then
    gradle_goodness gradle $testTarget test --rerun-tasks | addTimerDescription "gradle --rerun-tasks $testTarget test"
    print_on_error ${pipestatus[1]} "buildError: gradle $testTarget test"
  else
    gradle_goodness gradle $testTarget test --rerun-tasks \
        | sed '/output/,/^OpenJDK/{/output/!{/^OpenJDK/!d}}' \
        | addTimerDescription "gradle $testTarget test"
  fi ;

  gradle_goodness gradle $testTarget ctest | addTimerDescription "gradle $testTarget cleanTest test"
  print_on_error ${pipestatus[1]} "buildError: gradle $testTarget test"

  gradle_goodness gradle $testTarget2 ctest | addTimerDescription "gradle $testTarget2 cleanTest test"
  print_on_error ${pipestatus[1]} "buildError: gradle $testTarget2 test"

#  set +o pipefail

}

function addTimerDescription() {
  local description=${1:-no description} ; shift 1;
  cat \
    | sed -r 's|(.*real.*user.*sys)|\1      : '$description'|' \
    | sed -r 's|(.*BUILD FAILED.*)|        --- buildError: \1|' \
    | sed -r 's|(.*passing.*)|        --- (verification): \1|' \
    | sed -r 's|(.*junit test run passed.*)|        --- (verification): \1|'
}

function forceJava8 {

  #
  # Force JAVA 8 - due to legacy dependencies
  #
  export PATH="$HOME/.jenv/versions/1.8/bin:$PATH"
  export JAVA_HOME="$HOME/.jenv/versions/1.8"

}

function findLibJars() {
  local location=${1:-.}
  local filter=${2:-no}

  print_two_columns "location" "$location"
  print_two_columns "filter" "$filter"
  print_section "find $location -wholename '*/libs/*.jar' | grep -v sources | grep -v test.jar"

  if [[ "$filter" == "no" ]] ; then
    find $location -wholename '*/libs/*.jar' \
      | grep -v sources \
      | grep -v test.jar \
      | bat --theme="$batTheme" --paging=never
  else
    find $location -wholename '*/libs/*.jar' \
      | grep -- "$filter" \
      | grep -v sources \
      | grep -v test.jar \
      | bat --theme="$batTheme" --paging=never
   fi
}

function diffWars() {

  local baseLibLocation=${1:-/path/to/war1}
  local updatedLibLocation=${2:-/path/to/war2}
  local cleanUp=${3:-yes}

  print_section "War comparison: only jars names are compared"
  print_two_columns "baseLibLocation" "$baseLibLocation"
  print_two_columns "updatedLibLocation" "$updatedLibLocation"

  local file1="war-file-listing-1"
  local file2="war-file-listing-2"

  unzip -l $baseLibLocation | grep jar | awk '{ print $4 }' | sed 's|WEB-INF/lib/||g' | sort > $file1
  print_two_columns "baseLibLocation" "$baseLibLocation: $(cat $file1 | wc -l ) jars"

  unzip -l $updatedLibLocation | grep jar | awk '{ print $4 }' | sed 's|WEB-INF/lib/||g' | sort > $file2
  print_two_columns "updatedLibLocation" "$updatedLibLocation: $(cat $file2 | wc -l) jars"

  run_and_format diff $file1 $file2

  if [[ "$cleanUp" == "yes" ]] ; then
    run_and_format rm $file1 $file2
  else
    print_two_columns "skipped cleanup" "Files are: $file1 and $file2 in $(pwd)"
  fi ;
}

function diffLibJars() {

  local baseLibLocation=${1:-path/to/gradle1}
  local updatedLibLocation=${2:-path/to/gradle2}

  print_section "Jar comparison: only jars in lib; javadoc,test,sources are excluded"
  print_two_columns "baseLibLocation" "$baseLibLocation"
  print_two_columns "updatedLibLocation" "$updatedLibLocation"

  if ! test -d "$baseLibLocation" || ! test -d "$updatedLibLocation" ; then
    print_error "Verify folders exist" ;
    return ;
  fi ;

  local file1="output-diffLibJars-1"
  local file2="output-diffLibJars-2"
  find $baseLibLocation -wholename '*/libs/*.jar' | grep -v sources | grep -v javadoc | grep -v test.jar \
    | xargs -n1 basename \
    | sed 's|basePath\.||g' \
    | sort > $file1

  find $updatedLibLocation -wholename '*/libs/*.jar' | grep -v sources | grep -v javadoc | grep -v test.jar \
    | xargs -n1 basename \
    | sed 's|basePath\.||g' \
    | sort > $file2

  diff $file1 $file2 \
    | sed '/^---$/d' \
    | sed -E "s/(^[0-9,]+)[acd]([0-9]+)/\n\n==> line number:     $(basename $baseLibLocation): \1     \*$(basename $updatedLibLocation): \2/" \
    | sed 's|^> |     |' | sed 's|^< |    * |'

  rm $file1 $file2
}

function diffSimple() {

  local baseFileLocation=${1:-~/temp/java}
  local updatedFileLocation=${2:-~/temp/javaLib}
  print_section "Simple File comparison"
  print_two_columns "baseFileLocation" "$baseFileLocation"
  print_two_columns "updatedFileLocation" "$updatedFileLocation"

  if ! test -f "$baseFileLocation" || ! test -f "$updatedFileLocation" ; then
    print_error "Verify files exist" ;
    return ;
  fi ;

  local file1="output-diffSimple-1"
  local file2="output-diffSimple-2"
  cat $baseFileLocation \
    | sort > $file1

  cat $updatedFileLocation \
    | sort > $file2

  diff $file1 $file2 \
    | sed '/^---$/d' \
    | sed -E "s/(^[0-9,]+)[acd]([0-9]+)/\n\n==> line number:     $(basename $baseFileLocation): \1     \*$(basename $updatedFileLocation): \2/" \
    | sed 's|^> |     |' | sed 's|^< |    * |'

  rm $file1 $file2
}

startElapsedSeconds=$(date +%s)
function showElapsedTime() {
  local resetStart=${1:-false}

  local endSeconds
  local runtime

  if $resetStart ; then
    startElapsedSeconds=$(date +%s)
  else
      endSeconds=$(date +%s) ;
      runtime=$(( $endSeconds - $startElapsedSeconds)) ;
      formatedTime=$(date -d@$runtime -u "+%Mm %Ss") ;
      echo $formatedTime
  fi

}

function _serverLogs() {

  local command=${1}
  local commandOptions=${2}

#        "ScanComponents" \
   local searchPatterns=( \
    "WARNING" \
    "String 2:" \
    "String 3" \
    "\[   " \
    'some other string' \
    "Start Up complete")


  if [[ "$commandOptions" == "warn" ]] ; then
    searchPatterns+=( \
            "WARNING" \
            "ERROR" \
            )
  fi

  #ls
  location="path/to/log/file" ;
  local formatting="doFormat" ;
  if [[ "$commandOptions" != "" ]] ; then
    formatting="noFormat"
  fi ;
  local searchStrings=""
  for i in "${searchPatterns[@]}"
  do
     searchStrings+=" --regexp '$i' "
     # or do whatever with individual element of the array
  done
  print_line "searchStrings: $searchStrings"
  filterTail $location "$formatting" "$searchStrings"

}



function pids() {
  watch 'ps -ef | grep -v grep | \
    grep --only-matching \
      --regexp ".*csap=\w* " \
      --regexp ".*Dzookeeper*\w" \
      --regexp ".*mysqld " \
      | sed "s/^/\n/" \
      | sed "s/-D/\n\t\t\t-D/g" \
      | sed "s/-class/\n\t\t\t-class/g" \
      '
}

function showPids() {

	local filter=${1:-wd.tag=}
	local doKill=${2:-noKill}

  local matchCount=$({ docker ps ; ps -ef ; } | grep -v grep | grep "$filter" | wc -l) ;
  print_separator "'$filter'"
  if (( $matchCount == 0 )) ; then
    print_line "No Matches"
    return
  fi ;
  { docker ps ;} | grep -v grep | grep ".*${filter}*\w*" \
  				| sed 's/^/\n/' \
  				| bbat awk --plain --theme=$batTheme --paging=never

  {  ps -ef ;} \
    | grep -v grep | grep --only-matching ".*${filter}*\w*" \
    | sed 's/^/\n/' \
    | sed 's/-classpath/\n\t -classpath/g' \
    | sed 's/-D/\n\t -D/g' \
    | sed 's/-X/\n\t -X/g' \
    | sed 's/-j/\n\t -j/g' \
    | bbat awk --plain --theme=$batTheme --paging=never

  if [[ "$doKill" == "doKill" ]] ; then
    pidsToKill=$(ps -ef | grep -v grep | grep "$filter" | awk '{ print $2 }')
    print_block "Killing pid(s): $pidsToKill"
    ps -ef | grep -v grep | grep "$filter" | awk '{ print $2 }' | xargs kill -9
    showPids "$filter"
  fi ;
}

function getGradleCommand() {
    local command="./gradlew"
    echo $command
}

function noWarnings() {

  local suppressLine='         tasks.withType(JavaCompile) { options.compilerArgs += ["-nowarn", "-XDenableSunApiLintControl"] }'

  local theBuildFile="build.gradle"
  if test -f $theBuildFile ; then
    if ! grep "nowarn" $theBuildFile ; then
      print_block "updating $theBuildFile to disable build warnings"
      replace_first_match "testlogger" "\n$suppressLine\n     testlogger" $theBuildFile
    else
      print_block "found nowarn already"
    fi
  else
    print_block "run in folder containing build.gradle"
  fi
}

alias gg="gradle_goodness default"
alias ggpss="tt;pss gradle 900"
alias ggh="gradle_goodness help"
alias ggg="gradle_goodness gradle"
alias ggenv="gradle_goodness env"
alias ggq="gradle_goodness gradle"
alias ggdry="gradle_goodness dry"
alias ggstop="gradle_goodness stop"
alias ggtest="gradle_goodness test"
alias ggrebuild="gradle_goodness rebuild"
alias ggbuild="gradle_goodness build"
alias ggswitch="gradle_goodness switch"
alias goto="gradle_goodness switch"
alias ggtaskTree="gradle_goodness taskTree"
alias ggfilter="gradle_goodness filter"
alias ggdepends="gradle_goodness depends"
alias ggprojects="ggg projects"
alias ggreport="gradle_goodness report"
alias ggurl="git config --get remote.origin.url"



#unalias ggsup
function gradle_goodness() {

	local command=${1:-help}

	if (( $# > 0 )); then
	  shift
	fi

	local commandOptions="$*"

  local ijHackFile=".idea/misc.xml"
  if test -d build.gradle.kts && test -f $ijHackFile ; then
    if ! does_file_contain_word $ijHackFile "FrameworkDetectionExcludesConfiguration" ; then
      delete_all_in_file "FrameworkDetectionExcludesConfiguration" $ijHackFile
      add_after_match_in_file \
        "ProjectRootManager" \
        '<component name="FrameworkDetectionExcludesConfiguration"><type id="web" /></component>' \
        $ijHackFile
      fi
#
  fi

	case $command in

	  taskTree )
      print_section "tasks: $commandOptions"

      local gradleTask="${1:-assemble} taskTree"
      if (( $# > 1 )) || [[ "$gradleTask" == */*  ]]; then
        local taskToAnalyze=${2:-assemble}
	      gradleTask="$(gradlePath $1):$taskToAnalyze $(gradlePath $1):taskTree"
      fi ;
	    print_and_run $(getGradleCommand) --quiet  $gradleTask \
	      | grep -v "com.workday.quark"
	    ;;

	  filter )
	    #sed -E 's|.*Users|/Users|g' | grep Users | sed 's|: |\n\t|g'
      print_section "filter: $(pwd)"

      local gradleTask="${1:-checkstyleMain}"
      if (( $# > 1 )) || [[ "$gradleTask" == */*  ]]; then
        local projectFolder="$1"
        shift
        local task="${*:-checkstyleMain}"
        if [[ $projectFolder == "-" ]] ; then
          gradleTask="$task"
        else
	        gradleTask="$(gradlePath $projectFolder):$task"
	      fi
      fi ;
	    print_and_run $(getGradleCommand) --quiet  $gradleTask 2>&1 \
	      | sed -E 's|.*Users|/Users|g' \
	      | sed 's|: |\n\t|g'

	    ;;

	  stop )
      print_section "stop: $commandOptions"
	    _gradleCleanUp $*
	    ;;

	  depends )
      print_section "depends: $commandOptions"

	    _ggdepends compileClasspath $*
	    ;;

	  dependsTest )
      print_section "dependsTest: $commandOptions"

	    _ggdepends  testCompileClasspath $*
	    ;;


	  report )
      print_section "report: $commandOptions"
      print_two_columns "choices" "project(default), projectReport (gradle project Report),  build (build analysis), intraDepends(intra project), java(version)"

      local reportToRun="${1}"
      if (( $# > 0 )); then  shift ; fi ;

	    case "$reportToRun" in
      	      build )  show_build  ;;
      	      intraDepends | intraProjects )  show_project_depends  ;;
      	      projectReport )  _gg_project_report  $* ;;
      	      projects )  ggprojects  $* ;;
      	      java )
      	        show_java $*
      	        ;;
      	      * ) show_build ;;
      esac
	    ;;

	  switch )
      print_section "switch: $commandOptions"

	    case "$commandOptions" in
	      ij )  cd ~/IdeaProjects  ;;
	      wcsap | csap )  cd ~/IdeaProjects/wcsap  ;;
	      * ) cd ~/IdeaProjects/$commandOptions ;;
	    esac
	    # print_line "pwd: $(pwd)"
	    ;;

	  build )
	    print_section "build: $(pwd)"
      local projectFolder="$1"
      if (( $# > 0 )); then  shift ; fi ;
      local params="$*"

      local tasks="$(gradlePath $projectFolder):jar $(gradlePath $projectFolder):testJar"
      if grep -q 'nebula.integtest-standalone' $projectFolder/build.gradle.kts >/dev/null 2>&1 \
        || grep -q 'nebula.integtest-standalone' $projectFolder/build.gradle >/dev/null 2>&1 ; then
        tasks="$tasks $(gradlePath $projectFolder):integTestJar"
        tasks="$(gradlePath $projectFolder):jar"
      fi ;


      if [[ $projectFolder == "." ]] ; then
        tasks="clean jar"
      fi


      print_and_run $(getGradleCommand) --quiet $tasks $params

      print_and_run find $projectFolder -wholename '"*/build/*.jar"'

	    ;;

	  test )
      print_section "test: $commandOptions"
      project=$1
      shift
	    _ggtest $project $*
	    ;;

	  env )
      print_section "env: $(pwd)"
      local projectFolder="$1"
      if (( $# > 0 )); then  shift ; fi ;
      local params="$*"

      local tasks="$(gradlePath $projectFolder):buildEnvironment"
      print_and_run $(getGradleCommand) --quiet $tasks $params
	    ;;

	  dry )
      print_section "dry run: $commandOptions"
      local dryTask
      if (( $# == 1 )) ; then
        project="" ;
        dryTask=${1}
      else
        project="$(gradlePath $1):" ;
        dryTask=${2}
      fi
      shift
	    print_and_run $(getGradleCommand) --dry-run --quiet ${project}${dryTask}
	    ;;

	  rebuild )
	    print_section "rebuild: $(pwd)"
      local projectFolder="$1"
      if (( $# > 0 )); then  shift ; fi ;
      local params="$*"


      print_and_run $(getGradleCommand) --quiet \
        "$(gradlePath $projectFolder):clean $(gradlePath $projectFolder):jar $params"

      print_and_run find $projectFolder -wholename '"*/build/*.jar"'

	    ;;

	  default )
      local projectFolder="$1"
      shift;
      local params="$*"

      local gradleTask="$(gradlePath $projectFolder):$params"

      if [[ "$params" == "" ]] ; then
        gradleTask="$projectFolder"
      elif [[ "$projectFolder" == "-" ]] ; then
        gradleTask="$params"
      elif [[ "$projectFolder" == "" ]] ; then
        gradleTask="$params"
      fi
      print_and_run $(getGradleCommand) $gradleTask 2>&1
	    ;;

	  gradle )
#      print_two_columns "gradle" "$commandOptions"
      local projectFolder="$1"
      shift;
      local params="$*"

      local gradleTask="$(gradlePath $projectFolder):$params"

      if [[ "$params" == "" ]] ; then
        gradleTask="$projectFolder"
      elif [[ "$params" == "ctest" ]] ; then
        gradleTask="$(gradlePath $projectFolder):cleanTest $(gradlePath $projectFolder):test "
      elif [[ "$projectFolder" == "-" ]] ; then
        gradleTask="$params"
      elif [[ "$projectFolder" == "" ]] ; then
        gradleTask="$params"
      fi
      print_and_run $(getGradleCommand) --quiet  $gradleTask 2>&1
	    ;;


	  help | *)

      print_section "gradle_goodness"

      { print_separator "shortcuts" \
        && print_two_columns "<path> notation" "for convenience, paths will be converted to gradle project task (/ replaced by :)"  \
        && print_two_columns "ggh" "alias for gradle_goodness help"  \
        && print_two_columns "gg" "alias for gradle_goodness default. add - as first param to bypass path"  \
        && print_two_columns "ggg,ggq" "alias for gg gradle runs in quiet mode. add - as first param to bypass path"  \
        && print_two_columns "ggtest,ggbuild" "alias for gg test, gg build"  \
        && print_two_columns "ggdepends,ggtaskTree" "alias for gg depends, gg taskTree"  \
        && print_two_columns "ggreport" "alias for gradle_goodness report  project | projectReport | build | intraDepends | java )"  \
        && print_two_columns "ggstop" "alias for gg stop - kills running daemons"  \
        && print_two_columns "ggdry" "alias for gg dry - dry run of tasks"  \
        && print_two_columns "ggswitch <repo>" " everything else to ~/IdeaProjects/<repo>"  \
	      ; } | bbat yaml --plain --theme=$batTheme --paging=never

      { print_separator "build" \
        && print_two_columns "gradle <standard>" "eg. ggg path/to/gradle/module build <options>"  \
        && print_two_columns "build <standard>" "runs gradle assemble eg. ggtest path/to/gradle/module"  \
        && print_two_columns "test <standard>" "eg. ggtest path/to/gradle/module --tests '*hello*' "  \
        && print_two_columns "rebuild <path>" "runs gradle clean and assemble. eg. ggrebuild path/to/gradle/module "  \
	      ; } | bbat yaml --plain --theme=$batTheme --paging=never

      { print_separator "inspect" \
        && print_two_columns "taskTree <path> <task>" "shows task hierachy - install https://github.com/dorongold/gradle-task-tree"  \
        && print_two_columns "depends <path> <opt: lib>" "show dependency tree, optionally narrowed if a library is specified (eg. log4j)."  \
	      ; } | bbat yaml --plain --theme=$batTheme --paging=never

      print_line "\n\n"

	    ;;
	esac

}


function gradlePath() {
  local pathWithSlashes="${1%/}"

  local pathWithCustomModules="$pathWithSlashes"
  if [[ "$pathWithCustomModules" == *"someGradle/modules/"*"/impl" ]] ; then
    local parentName=$( basename $(dirname $pathWithCustomModules))
    pathWithCustomModules=$(echo $pathWithCustomModules | sed "s|/impl$|/${parentName}-impl|")

  elif [[ "$pathWithCustomModules" == *"someGradle/modules/"*"/api" ]] ; then
    local parentName=$( basename $(dirname $pathWithCustomModules))
    pathWithCustomModules=$(echo $pathWithCustomModules | sed "s|/api|/${parentName}-api|")
  elif [[ "$pathWithCustomModules" == *"modules/"*"/impl" ]] ; then
    local parentName=$( basename $(dirname $pathWithCustomModules))
    pathWithCustomModules=$(echo $pathWithCustomModules | sed "s|/impl$|/${parentName}-impl|")

  elif [[ "$pathWithCustomModules" == *"modules/"*"/api" ]] ; then
    local parentName=$( basename $(dirname $pathWithCustomModules))
    pathWithCustomModules=$(echo $pathWithCustomModules | sed "s|/api|/${parentName}-api|")
  fi
  local pathWithColons="${pathWithCustomModules//\//:}"
  echo "$pathWithColons"
}


function _ggdepends () {


  local config=${1:-compileClasspath}
  local projectFolder=${2:-}
  local artifact=${3:-none}

  local runConfiguration=""

  local gradleTask="$(gradlePath $projectFolder):dependencyInsight"

  if [[ "$artifact" == "none" ]] ; then
    gradleTask="$(gradlePath $projectFolder):dependencies --configuration $config"
  else
    runConfiguration="--dependency $artifact"
  fi


  print_and_run $(getGradleCommand) --quiet  $gradleTask $runConfiguration

}

function demoJqJson() {
  x=$(curl --silent --header 'Content-Type: application/json' "https://jsonplaceholder.typicode.com/posts")
  print_command "Demo jq --null-input \$x" "$(jq --null-input "$x")"
  print_command "Demo: jq --null-input \$x | jq '.[1].id'" "$(jq --null-input "$x" | jq '.[1].id')"
}

bbUrl="https://bitbucket.yourcompany.com/rest/api/1.0"
function bbDemo() {

  listCommits="$bbUrl/projects/path/to/repor/commits/?until=master&limit=1"
  listPrs="$bbUrl/projects/path/to/repor/pull-requests/?limit=1&state=merged"
  listMyPrs="$bbUrl/dashboard/pull-requests/?limit=1&state=merged"

  curl --request GET \
        --header "Authorization: Bearer $bitBucketAccess" \
        --header 'Accept: application/json' \
        "$listMyPrs" \
        | jq
}


function addPrComment() {
    local id="$1"
    local comment="$2"

    print_two_columns "addPrComment" "id $id , comment: $comment"
    local createCommentUrl="$bbUrl/projects/path/to/repor/pull-requests/$id/comments"

    curl --request POST --silent \
        --header "Authorization: Bearer $bitBucketAccess" \
        --header 'Content-Type: application/json' \
        --data '{
                  "text": "'$comment'"
                }' \
        "$createCommentUrl"
}

function createPr() {
  local branchName="$1"
  local title="$2"
  local createPrUrl="$bbUrl/projects/path/to/repor/pull-requests"

  curl --request POST --silent \
      --header "Authorization: Bearer $bitBucketAccess" \
      --header 'Content-Type: application/json' \
      --data '{
              "title": "'$title'",
              "description": "'$title'",
              "state": "OPEN",
              "open": true,
              "closed": false,
              "fromRef": {
                  "id": "refs/heads/'$branchName'",
                  "repository": {
                      "slug": "yourRepo",
                      "name": null,
                      "project": {
                          "key": "yourRepo"
                      }
                  }
              },
              "toRef": {
                  "id": "refs/heads/master",
                  "repository": {
                      "slug": "yourRepo",
                      "name": null,
                      "project": {
                          "key": "yourRepo"
                      }
                  }
              },
              "locked": false,
              "reviewers": [
                  {
                      "user": {
                          "name": "your.name"
                      }
                  }
              ]
          }' \
      "$createPrUrl"
}


function print_process() {
	printf "\n\n==> Processing: %-40s path: %-30s %s\n" "$@" ;
}



function _ggtest() {


  local gradleTask="test"
  local paramsWildCardEscaped

  if ((  $# == 0  )) ; then
    print_two_columns "default" "test"
  else

    local projectFolder=${1:-}
    shift ;
    local params="$*"
    paramsWildCardEscaped="${params//\*/\\*}"
    gradleTask="$(gradlePath $projectFolder):cleanTest $(gradlePath $projectFolder):test"

  fi ;

  #echo "paramsWildCardEscaped'$paramsWildCardEscaped'"

  # --no-build-ca --rerun-tasks
  print_and_run $(getGradleCommand) --quiet --no-build-cache $gradleTask $paramsWildCardEscaped
}


alias gitt="git --no-pager"

function gitWipe() {
  local isForce=${1:-false}

  if $isForce || confirmation_prompt "Proceed with complete wipe of current git 'git reset --hard && git clean -fdx' ?" ; then
      git reset --hard && git clean -fdx
  fi ;
  # prune empty dirs?
  #git clean -fd

  git status | bat --theme="$batTheme"  --language=java --plain
}

function gitRestore() {
  local folder=${1:-.}
  local isForce=${2:-false}

  if $isForce || confirmation_prompt "Proceed with restore of $folder git 'git restore --source=HEAD --staged --worktree --  $folder' ?" ; then
      git restore --source=HEAD --staged --worktree --  $folder
  fi ;

  git status | bat --theme="$batTheme"  --language=java --plain

}


function gitZip() {

  local zipFile="${1:-changed-files.zip}"
  print_block "Creating $zipFile using 'gitt diff --name-only master...'"
  zip $zipFile $(gitt diff --name-only master...)
  #unzip -l $zipFile

}

function gitSquash() {

  local comment="$*"

  if [[ "$comment" == "mandatory" ]] ; then
    print_error "comment is mandatory for commit and must start with JIRA"
    return 99
  fi

  git reset --soft $(git merge-base master HEAD)
  git commit -m "$comment"
  git push --force

  git status | bat --theme="$batTheme"  --language=java --plain
}

function gitClean() {

  print_super "Starting preview"
  git clean --dry-run -dx
  if ! confirmation_prompt "Proceed with 'git clean -dx' ?" ; then
    print_subsection "Clean Skipped"
    return ;
  fi ;

  git clean -dx

}

function showFile() {

  print_section "the 'file' command inspects the file metadata"

  local filePath=${1:-add-path-to.file}

  file $filePath

  classType=$( file $filePath | awk '{print $7 ;}') ;
  print_two_columns "type" "$classType"

  case "$classType" in

    "61.0" ) print_subsection "Java 17" ;;

    "62.0" | "63.0" | "64.0" | "65.0" | "66.0" ) print_subsection "later then Java 17" ;;

    "60.0" | "59.0" | "58.0" | "57.0" | "56.0" | "55.0" | "54.0" | "53.0" ) print_subsection "Java 9 thru Java 17" ;;

    "52.0") print_subsection "Java 8" ;;
    "51.0") print_subsection "Java 7" ;;
    "50.0") print_subsection "Java 6" ;;
    "49.0") print_subsection "Java 5" ;;
    *) print_subsection "Other Java (pre java 5, post 22)" ;;
  esac
}

function gitRebaseMaster() {

  local pattern=${1:-param 1 is pattern};
  local previewMode=${2:-true}

  print_two_columns "pattern" "$pattern"
  print_two_columns "previewMode" "$previewMode"

  print_subsection "Starting preview"

  for branchName in $(gitt branch | grep "$pattern" ) ; do
    print_subsection "branchName" "$branchName"

    if $previewMode ; then
      print_and_run "echo git checkout master \&\& git checkout $branchName \&\& git rebase master \&\& git push --force"
    else
      print_and_run "git checkout master && git checkout $branchName && git rebase master && git push --force"
    fi
  done

}

function gitBranches() {
  local pattern=${1:-}
  local remote=${2:-false}
  local csv=${3:-false}

  print_two_columns "pattern" "'$pattern'"
  print_two_columns "remote" "'$remote'"
  print_two_columns "csv" "'$csv'"

  local gitParam=""
  if $remote ; then
    gitParam="--remote"
  fi ;

  print_separator "output"

  if $csv ; then
    gitt branch $gitParam | grep "$pattern" | sed 's|origin/||' | sed -z 's|\n|,|g' | sed 's|  *||g'
  else
    gitt branch $gitParam | grep "$pattern"
  fi
}

function gitLocalDiffs() {

  local pattern=${1:-param 1 is pattern};

  print_two_columns "pattern" "$pattern"

  print_subsection "Diffs with master"

  for branchName in $(gitt branch | grep "$pattern" ) ; do
    print_subsection "branchName" "$branchName"

    print_and_run "gitt checkout $branchName && gitt diff master --name-only"

  done

}

function pomDiff() {
  local pom1=${1:-}
  local pom2=${2:-}

  print_section "pomDiff --unified"
  diff --unified $(stripPomFile $pom1)  $(stripPomFile $pom2)


  print_section "pomDiff: original vs new"
  diff $(stripPomFile $pom1)  $(stripPomFile $pom2)

}

function stripPomFile() {
  local pom1=${1:-}
  local stripped=$(dirname $pom1)/stripped-sorted-pom.txt
  #print_section "creating $stripped"
  cat $pom1 \
    | grep -v  UTF \
    | grep --regexp "version" --regexp "artifact" \
    | tr '\n' ' ' \
    | sed 's/ <artifactId>/\n/g' \
    | sed 's/<\/artifactId>/ /g' \
    | sed 's/<\/version>/ /g' \
    | sed 's/<version>/ /g' \
    | sed 's/  */ /g' \
    | sort \
    > $stripped

  echo $stripped
}

function _gg_project_report () {

  local artifact=${1:-none}
  local refresh=${2:-yes}

  local theReport="${CORE_REPO}/theReport/build/reports/project/dependencies.txt"


  print_two_columns "artifact" "$artifact"
  print_two_columns "refresh" "$refresh"
  print_and_run $(getGradleCommand) --quiet projectReport

}


function curltext() {
  local url="${1:-https://www.cnn.com}"
  print_separator "curl --silent $url"
  curl --silent "$url" | pandoc -f html -t plain |  bbat java --theme=$batTheme --paging=never
}

function htopMemory() {
  htop --sort-key M_RESIDENT
}


function _gradleCleanUp() {
  local doKill=${1:-no}

	print_section "doKill: '$doKill'"

  print_and_run $(getGradleCommand) --stop
  if [[ "$doKill" == "no" ]] ; then
    print_two_columns "note" "use ggstop y to do a kill -9 on the processes shown"
  fi ;

	local processes=("GradleDaemon" "GradleWrapperMain start" "gradle-wrapper" "bat --theme" "bat --language")
	processes+=("kotlin-compiler-embeddable")
  local numberToClean
  for process in "${processes[@]}" ; do
    numberToClean=$(pgrep -lf "$process" | wc -l) ;
    print_separator "'$process', found: $numberToClean"
    if (( $numberToClean > 0 )) ; then
      run_and_format "pgrep  -lf '$process'"

      if [[ "$doKill" != "no" ]] ; then
        run_and_format "pkill -9  -f '$process'"
        run_and_format "pgrep  -lf '$process'"
      fi ;
    fi
  done

}

function gradle() {
	gradleCommand "$*"
}


function gradleCommand() {

  local command="$(getGradleCommand)"

#	print_two_columns "location" "$(pwd)"
#	print_two_columns "command" "$command"
	echo "$command $*" | sed 's/ / \\\n\t /g'

	if isInteractiveTerminal ; then
	  { echo "Build folder: $(pwd)" && eval $command  $* ;} | bbat java --theme=$batTheme --paging=never
  else
    echo "Build folder: $(pwd)" && eval $command  $* ;
  fi

}

function serverLog() {

	local location=${1:-path/to/server.log};
  tail -n500 -F $location | bat --language=java --theme=$batTheme --paging=never
}

function filterTail() {

	local location="${1}";
	local formatCommand="${2:-doFormat}";
	local searchPatternAsGrepRegEx="${3:-none}";
#	shift; shift
#	local searchPatterns=("$@");
	local grepOptions="";
  if [[ "$location" != "" ]] && test -f "$location" ; then

#    searchPatternAsGrepRegEx=$(buildGrepPatternFromArray ${searchPatterns[@]} )
    grepOptions="--no-group-separator" ;
    linesBeforeMatch=1
    linesAfterMatch=1
    initialTailLines=$(wc -l < $location)


    print_two_columns "search" "$searchPatternAsGrepRegEx"
    print_two_columns "location" "$location"
    print_two_columns "initialTailLines" "$initialTailLines"
    print_two_columns "linesBeforeMatch" "$linesBeforeMatch"
    print_two_columns "linesAfterMatch" "$linesAfterMatch"
    print_two_columns "grepOptions" "$grepOptions"

    print_separator "ctrl-c to exit"

    if [[ "$formatCommand" == "doFormat" ]] &&  isInteractiveTerminal ; then
     tail -n$initialTailLines -F $location \
            | eval grep --line-buffered \
                      --after-context=$linesAfterMatch \
                      --before-context=$linesBeforeMatch \
                      $searchPatternAsGrepRegEx \
            | bat --language=java --theme=$batTheme --paging=never
    else

     tail -n$initialTailLines -F $location \
            | eval grep --line-buffered \
                      --after-context=$linesAfterMatch \
                      --before-context=$linesBeforeMatch \
                      $searchPatternAsGrepRegEx
    fi


  else
    print_error "Location not found: '$location'"
  fi;
}

function silentCleanGradle() {

	print_subsection2 "Shutting gradle daemons down to address leaks"
	ggstop yes &> lastGradleClean.txt

}


#
#  zookeeper: do:  sudo visudo ; and add: YOUR_USERID ALL=(ALL) NOPASSWD: ALL
#
zooHome="/opt/zookeeper-3.6.2"
zooConfFile="$zooHome/conf/zoo.cfg"
#zooLogs="$zooHome/logs/zookeeper-peter.nightingale-server-$(hostname).out"
zooLogs="$zooHome/logs/zookeeper-root-server-$(hostname).out"
zooServiceFile="/Library/LaunchDaemons/com.workday.zookeeper.plist"

function zoo() {

	local command=${1:-help}
	shift

	local commandOptions="$*"

	if [[ "$commandOptions" != "" ]] ; then
		print_two_columns "commandOptions" "$commandOptions"
	fi ;
	print_two_columns "zooHome" "$zooHome"
	print_two_columns "zooLogs" "$zooLogs"
	print_two_columns "zooConfFile" "$zooConfFile"
	print_two_columns "zooServiceFile" "$zooServiceFile"
	print_separator "zoo helper: $command"

	case $command in

	  home )
	    cd $zooHome
	    ;;

	  service )
	    batt $zooServiceFile
	    ;;

	  ps )
	    pss zookeeper
	    ;;


	  conf | config )
	    #cat /usr/local/etc/my.cnf | sed '/^#/d' | { eval bat $batDefaultParameters }
	    batt $zooConfFile
	    ;;


	  confvi | vi )
	    sudo vi +'set ft=sh' $zooConfFile
	    # vim +'set ft=markdown' file
	    ;;

	  start | restart )
			#run_and_format "sudo sh -c '$zooHome/bin/zkServer.sh stop && rm -rf $zooLogs && $zooHome/bin/zkServer.sh start'"
			sudo sh -c "rm -rf $zooLogs && $zooHome/bin/zkServer.sh start"
	    #sudo $zooHome/bin/zkServer.sh start
	    ;;

	  stop )
	    sudo $zooHome/bin/zkServer.sh stop
	    ;;

	  log | logs | tail )
	    mtail --retry -n 5000   $zooLogs
	    ;;


	  status )
	    # run_and_format "mysql cli --execute='show variables' | bat --theme=$batTheme --language=bash"
	    $zooHome/bin/zkCli.sh stat /
	    ;;

	  cli )
	    $zooHome/bin/zkCli.sh
	    ;;


	  ui )
	    if [[ "$commandOptions" == "start" ]] ; then
				drun zoo-admin -p 9000:9000 elkozmon/zoonavigator:latest
				print_section "use browser http://localhost:9000 to connect to zookeeper docker.for.mac.host.internal:2181"

	    else
				docker stop zoo-admin
	    fi
	    ;;



	  help | *)
	    print_two_columns "start|stop" "start|stop localhost"
	    #print_two_columns "cli" "mysql cli"
	    print_two_columns "logs" "tail logs"
	    #print_two_columns "vars <filter>" "show variables, with an optional filter"
	    print_two_columns "conf" "show $zooConfFile"
	    print_two_columns "confvi" "edit $zooConfFile"
	    print_two_columns "ui start | stop" "start or stop docker zookeeper ui"
	    ;;
	esac


}

wcsapHome="/Users/peter.nightingale/IdeaProjects/wcsap"
wcsapInstallerHome="$wcsapHome/artifacts-configurations/csap-package-linux/installer"

function server_install() {

	local installHost=${1:-none}
	local isTest=${2:-test}


	print_two_columns "wcsapHome" "$wcsapHome"
	print_two_columns "wcsapInstallerHome" "$wcsapInstallerHome"
	print_separator "install host:'$installHost'"

	if [[ "$installHost" == "none" ]] ; then
		print_section "host is a required parameter"
		return 99
	fi ;

	local originalFolder=$(pwd)
	cd $wcsapInstallerHome ;

	if [[ "$isTest" == "test" ]] ; then
		bash -c "./remote-install-test.sh $installHost" | bat $batDefaultParameters
		print_section "add runit to do install"
	else
		# bash -c "./remote-install.sh $installHost  no -noPrompt -ignorePreflight   -runCleanUp  -installCsap default -csapDefinition default"
		bash -c "./remote-install.sh  \
			$installHost no -noPrompt  -ignorePreflight  -runCleanUp \
			-autoPlaySourceFile /home/$(whoami)/csap-auto-play.yaml  \
			-installCsap default -csapDefinition default"
	fi

	cd $originalFolder


}




function trimJiraLog() {

	local theLogFile=${1}

	print_two_columns "theLogFile" "'$theLogFile'"

	local deletePatterns=( \
	    "Compute revision" \
	    "/usr/bin/git" \
	    "new tag" \
	    "new branch" \
	    "\[Step 6" \
	    "\[Step 7" \
	    "junit-platform.properties" \
	    "Using the default Artifactory url" \
	    "Using publish code line release" \
	    "Using Artifactory user" \
	    "Using Artifactory password" \
	    "Configured settings to access Artifactory" \
	    "Added library" \
	    "Building against code line" \
	    "Using Gradle 7.5.1" \
	    "Configured projects to access Artifactory" \
	    "\[Test Output" \
	    "\[JUnit Jupiter" \
	    "replaceme" \
	    "replaceme" \
    )


	local filename=$(basename -- "$theLogFile")
	local extension="${filename##*.}"
	filename="${filename%.*}"

	local trimmedFile=$(dirname $theLogFile)/$filename-filtered.$extension

	print_two_columns "trimmedFile" "'$trimmedFile'"

	if test -f $theLogFile ; then

		cp --verbose --force $theLogFile $trimmedFile
		#print_array_in_columns "Patterns being deleted" deletePatterns 40

	  for deletePattern in "${deletePatterns[@]}" ; do
	    delete_all_in_file "$deletePattern" "$trimmedFile"
	  done

	  replace_all_in_file ': Step ' "\n\n\n\n\n_________________________________________________________________\n\n>>> : Step " $installerFile

	else
		print_section "Exiting - not found"
	fi

}


function grabLatestLogs() {

	local latestDownloadZip="$(ls -t ~/Downloads/*.zip | head -1)"

	print_two_columns "latestDownloadZip" "'$latestDownloadZip'"

	local NOW=$(date +"%h-%d--%I-%M")

	local destination=$(pwd)/logs-$NOW;

	if test -f $latestDownloadZip ; then
		mkdir -p $destination
		cd $destination;
		print_two_columns "Extracting" "$(pwd)"
		unzip -qq -o $latestDownloadZip


		print_two_columns "Extracting" "build-logs"
		mkdir -p build-logs
		unzip -qq -o build*.zip -d build-logs

		print_two_columns "build-logs" "removing empty files"
		find build-logs -type f -empty -print -delete

		trimJiraLog build-logs/theBuildLogForJiraCreator.txt

		cd ..

	else
		print_section "Exiting - not found"
	fi
}


function dlogin() {

      docker login the-docker-repo.yourcompany.com \
        --username=$myRepoUser --password=$myRepoPass

      docker login docker-public-artifactory.yourcompany.com \
        --username=$myRepoUser --password=$myRepoPass
}


function services() {

	local filter=${1:-};
	local command=${2:-};

	if [[ "$filter" == "" ]]  ; then
		sudo launchctl list | bbat java --theme=$batTheme --paging=always
	elif [[ "$command" != "" ]] ; then
		sudo launchctl $command $filter | bbat java --theme=$batTheme --paging=never
	else
		sudo launchctl list \
			| grep -- "$filter" \
			| grep -v grep | sed 's/^/\n\n/' \
			| bbat java --theme=$batTheme --paging=never
	fi

}



#
#  mysql
#
# mysqlHome="/usr/local/opt/mysql@5.7"
#mysqlHome="/opt/homebrew/opt/mysql-client"
mysqlHome="$MYSQL_HOME"
mysqlLogFile="$HOME/mysqlLogs/mysql.log"
mysqlConfFile="$(brew --prefix)/etc/my.cnf"

myDbName="my-mysql-server"
myDbUiName="my-mysql-ui"
function myMysql() {
  local command=${1:-help}
  local mysqlDockerTag=${myDbVersion:-latest}

  print_two_columns "mysqlDockerTag" "$mysqlDockerTag"

  case $command in

	  start )
      docker login the-docker-repo.yourcompany.com \
        --username=$myRepoUser --password=$myRepoPass

      docker login docker-public-artifactory.yourcompany.com \
        --username=$myRepoUser --password=$myRepoPass

      local mysqlImage="the-docker-repo.yourcompany.com"
      mysqlImage+="/mysqldocker:$mysqlDockerTag"

      drun $myDbName --publish 3306:3306 \
        $mysqlImage

      # get an admin web ui to verify
      drun  $myDbUiName --publish 9080:80 \
        --env PMA_HOST=host.docker.internal \
        --env PMA_PORT=3306 phpmyadmin:latest
	    ;;

	  stop )

      docker stop $myDbName
      docker rm --force $myDbName
      docker stop $myDbUiName
      docker rm --force $myDbUiName
	    ;;

	  reload )
      print_separator "Add steps to reload"

	    ;;

    help | *)

      print_two_columns "start|stop|reload" "commands"

      print_separator "Running  Processes"
      showPids "mysql"
      ;;
  esac

  # verify on browser
  #	http://localhost:9080
}

function mmysql() {

	local command=${1:-help}

	local commandOptions=""
	if (( $# > 0 )); then
	  shift
	  local commandOptions="$*"
		print_two_columns "commandOptions" "$commandOptions"
	fi

	print_two_columns "mysqlHome" "$mysqlHome"
	print_two_columns "mysqlConfFile" "$mysqlConfFile"
	print_two_columns "mysqlLogFile" "$mysqlLogFile"
	print_separator "mysql helper: $command"

	case $command in

	  conf | config | my.conf)
	    cat $mysqlConfFile | sed '/^#/d' | bat $batDefaultParameters
	    #catt $mysqlConfFile
	    ;;


	  confvi | vi )
	    vi +'set ft=sh' /usr/local/etc/my.cnf
	    # vim +'set ft=markdown' file
	    ;;

	  start | restart )
			run_and_format "truncate -s 0 $mysqlLogFile"
	    brew services restart mysql@5.7
	    ;;

	  stop )
	    brew services stop mysql@5.7
	    ;;

	  logs )
	    mtail  $mysqlLogFile
	    ;;

	  vars )
	    if [[ "$commandOptions" != "" ]] ; then
	    	mmysql cli --execute='show variables' | grep "$commandOptions"  | ff
	    else
	    	mmysql cli --execute='show variables'  | ff
	    fi
	    ;;



	  databases | db )
	    mmysql cli --execute='show databases' | grep "$commandOptions"  | ff
	    ;;

	  status )
	    if [[ "$commandOptions" != "" ]] ; then
	    	mmysql cli --execute='show status' | grep "$commandOptions"  | ff
	    else
	    	mmysql cli --execute='show status'  | ff
	    fi

	    ;;

	  cli )
	    mysql --host=127.0.0.1 --user=root --password=nyw "$*"
	    #$mysqlHome/bin/mysql --host=127.0.0.1 --user=root --password=nyw "$*"
	    ;;


	  ui )
	    if [[ "$commandOptions" == "start" ]] ; then
				drun php-admin -p 9080:80 \
               -e PMA_HOST=host.docker.internal \
               -e PMA_PORT=3306 phpmyadmin:latest
	    else
				docker stop php-admin
	    fi
	    ;;

	  PATTERN_N)
	    STATEMENTS
	    ;;

	  help | *)
	    print_two_columns "start|stop" "start|stop localhost"
	    print_two_columns "cli" "mysql cli"
	    print_two_columns "ui" "start|stop:  docker phpadmin"
	    print_two_columns "logs" "tail logs"
	    print_two_columns "vars <filter>" "show variables, with an optional filter"
	    print_two_columns "conf" "show $mysqlConfFile"
	    print_two_columns "confvi" "edit $mysqlConfFile"
	    ;;
	esac


}



function yaml() {

	clear ;
	print_section $1
	yq $1 ;
}






function gmits() {
	local days=${1:-1};
	local doClear=${2:-true};

	if $doClear ; then
		clear ;
		print_section "git commits last: $days days"
	fi ;

	#glo --since="$days days ago"
	# git log --since="$days days ago" --decorate --pretty=format:"%h%x09%an%x09%ad%x09%s"
	# git log --oneline --decorate --since="1 days ago" --date=format:'%Y-%m-%d %H:%M:%S'  --pretty=format:"%<(15,trunc)%h %<(25,trunc)%an %ad %s"
	# git log --oneline --decorate --since="1 days ago" --date=format:'%b %d %H:%M'  --pretty=format:"%<(15,trunc)%h %<(25,trunc)%an %<(20,trunc)%ad %s"
	# git log --oneline --decorate --since="1 days ago" --date=format:'%b %d %H:%M'  --pretty=format:"%<(15,trunc)%h %<(25,trunc)%an %<(20,trunc)%ad%C(auto,blue) %s"
	git log --oneline --decorate --since="$days days ago" --date=format:'%b %d %H:%M'  --pretty=format:"%<(15,trunc)%h %<(25,trunc)%an %C(auto,yellow)%>(16,trunc)%ad   %C(auto,red)%d%C(auto,blue) %s"
}

function gtags() {
	local days=${1:-1};
	local filter=${2:-not-specified};

	clear ;
	print_section "git commits last '$days' days, filter: '$filter'"

	if [[ "$filter" == "not-specified" ]] ; then
		gmits $days false;

	else
		gmits $days false | grep --regexp '(tag' --regexp "$filter" | sed "1,/$filter/!d" | grep --regexp '(tag' --regexp "$filter"
	fi ;


}


alias ns="netstat -vanp tcp | grep"



#alias llsof="lsof -i -n -P | grep TCP "



function artlogin() {

	echo "$myRepoPass" | docker login the-docker-repo.yourcompany.com -u="$myRepoUser" --password-stdin
	echo "$myRepoPass" | docker login docker-public-artifactory.yourcompany.com -u="$myRepoUser" --password-stdin

}

function images() {

	clear

	if (( $# == 0 )) ; then

		# docker images --format '{{json .}}' | jq
		docker images ;
		return ;
	fi ;

	local limit=${1:-10};
	local repo=${2:-the-docker-repo};

	print_section "Getting images"
	print_two_columns "limit" "$limit"
	print_two_columns "repo" "$repo"
	print_two_columns "command" "curl -u${myRepoUser}:\$artPass --request GET \"https://$repo.yourcompany.com/v2/_catalog?n=$limit\" 2>/dev/null| jq"

	curl -u${myRepoUser}:$artPass --request GET "https://$repo.yourcompany.com/v2/_catalog?n=$limit" 2>/dev/null| jq

}


function tags() {

	local image=${1:-nginx};
	local repo=${2:-the-docker-repo};

	print_section "Getting tags"
	print_two_columns "image" "$image"
	print_two_columns "repo" "$repo"

	print_two_columns "command" "curl -u${myRepoUser}:\$artPass --request GET \"https://$repo.yourcompany.com/v2/${image}/tags/list\" 2>/dev/null| jq"
	curl -u${myRepoUser}:$artPass --request GET "https://$repo.yourcompany.com/v2/${image}/tags/list" 2>/dev/null| jq

}

#
# General aliases
#


alias s="source ~/.zshrc;typeset -U PATH path"

alias java_home="/usr/libexec/java_home"

alias dow="cd ~/Downloads"

alias c="alias c1;alias c2;"
alias c1="labelSsh csap-01.root"
alias c2="labelSsh csap-02.root"

function addgnu() {
	# export PATH="/usr/local/opt/coreutils/libexec/gnubin:/usr/local/opt/gnu-sed/libexec/gnubin:$PATH"
	# print_command "legacyDependency path" "$(echo $PATH)"
	export GNUBINS="$(find /usr/local/opt -type d -follow -name gnubin -print)";

	for bindir in "${GNUBINS[@]}"; do
	  export PATH=$bindir:$PATH;
	done;

	print_command \
		"added gnubin to front of PATH" \
		"$(echo $PATH | tr ':' '\n'; echo -e '\n\n cp info:\n ';which cp ; cp --version; )"
}

alias setupCsapVM="cd /Users/peter.nightingale/git/csap-packages/csap-package-linux/installer; ./remote-install.sh csap-02.root  no -noPrompt -ignorePreflight -csapVmUser peter.nightingale -runCleanUp -deleteContainers -installDisk default -installCsap default -csapDefinition default"


function runInstaller() {
	local targetHost=${1:-csap-02.user} ;
	cd /Users/peter.nightingale/git/csap-packages/csap-package-linux/installer;
	chmod 755 remote-install.sh ;
	./remote-install.sh $targetHost no \
		-noPrompt -ignorePreflight  -runCleanUp -deleteContainers \
		-forcePackages \
		-installDisk default -installCsap default -csapDefinition default
}


function tabname {
     printf "\e]1;$1\a"
}

alias a="alias a1;alias a2"
alias a1="labelSsh aws-01.aws"
alias a2="labelSsh aws-02.aws"

alias a11="aws 11.22.33.44"
alias a1scp="aws_scp aws-01.aws"

function aws {

	local theHost=${1:-} ;

	DISABLE_AUTO_TITLE="true" ;

	tabname $theHost ;

	ssh -i $HOME/.ssh/csap-test.pem rocky@$theHost ;


}

function aws_scp {

	local theHost=${1:-} ;
	local theFile=${2:-} ;

	DISABLE_AUTO_TITLE="true" ;

	tabname $theHost ;

	scp -i $HOME/.ssh/csap-test.pem $theFile rocky@$theHost:


}

DISABLE_AUTO_TITLE="true" ;
#tabname "my-mac"

function labelSsh {

	local theHost=${1:-} ;

	DISABLE_AUTO_TITLE="true" ;

	tabname $theHost ;

	ssh $theHost ;


}


#
# iterm
#


i2() {
    osascript &>/dev/null <<EOF
      tell application "iTerm2"
        activate
        tell current window to set tb to create tab with default profile
        tell current session of current window to set newSplit to split horizontally with same profile
        tell newSplit
          select
          write text "pwd"
        end tell
      end tell
EOF
}

ik() {
    osascript &>/dev/null <<EOF
      tell application "iTerm2"
        activate
        tell current window to set tb to create tab with term-1 profile
        tell current session of current window to write text "pwd"
      end tell
EOF
}


isimple() {
    osascript &>/dev/null <<EOF
      tell application "iTerm2"
        activate
        tell current window to set tb to create tab with default profile
        tell current session of current window to write text "pwd"
      end tell
EOF
}



# alias k="kubecolor"


#alias dprune="run_and_format docker system prune --force; ; run_and_format docker image prune --all"

function dprune() {
  print_and_run docker system prune --force
  print_block Image prune
  print_and_run docker image prune --force --all
}

alias dvolumes="_docker 'volume ls'";
alias dps="_docker ps";
alias dimages="_docker images"


function _docker() {
  local command=${1:-containers}
  local filter=${2:-none}

	if [[ "$filter" == "none" ]] ; then
		docker $command | bbat js --theme=$batTheme --paging=never
  else
    docker $command | grep \
      --regexp "IMAGE" \
      --regexp "VOLUME" \
      --regexp "$filter" \
      |  bbat js --theme=$batTheme --paging=never
	fi ;

}

function dinteractive() {

  local imageName=${1:-hello-world}
  shift
  local params="$*"
  local containerName="${imageName%%:*}"
  containerName="${containerName##*/}"

  print_and_run docker run --init --interactive --tty --rm --name $containerName $params $imageName

}


function drunit() {

	print_subsection "Usage: drunit <container name> <volumes/ports/image>"

	local containerName="${1:-demo}" ;

  shift 1
  local volsPortsImage="$*"
  if [[ "$volsPortsImage" == "" ]] ; then
    volsPortsImage="--publish=9210:9210 the-docker-repo.yourcompany.com/csapplatform/test-utils"
  fi

  if [[ "$containerName" == *"Bash"* ]] ; then
    volsPortsImage="--entrypoint bash $volsPortsImage"
  fi

  local interactive="" ;
  if test -t FD ; then
    interactive="--interactive" ;
  fi

	# docker run -it  --name peter --rm -v $(pwd)/build:/transfer -p 9210:9210 the-docker-repo.yourcompany.com/csapplatform/test-utils:lates

	print_and_run docker run --rm $interactive  --tty \
		--name $containerName \
		$volsPortsImage
}


function drun() {

	# clear ;

	local containerName="${1:-missing-name}" ;

	if [[ "$containerName" == "missing-name" ]] ; then
		print_section "Usage: drun <name> <ports volumes image>"
		return ;
	fi ;

	shift 1
  local volsPortsImage="$*"

	# docker run -it  --name peter --rm -v $(pwd)/build:/transfer -p 9210:9210 the-docker-repo.yourcompany.com/csapplatform/test-utils:lates

	#set -x
	echo "docker run --rm --detach --name $containerName $volsPortsImage" | sed 's/--/\\\n\t--/g'
	echo
	eval docker run --rm --detach \
		--name $containerName \
		$volsPortsImage
	#set +x
}


function print_volume() { printf "%15s     %-20s %-20s \n" "$@" ; }
alias dv=dvols

function volumeReport() {

#	run_and_format docker system df -v
  _docker "system df -v"

	return

	print_section "Volume Totals by container                 Note this can take several minutes to compute"


	print_volume 'Total(mb)' "Container Name"
	print_volume "---------" "------------------------------------"


	local containerIds=$(docker ps --quiet);

	for containerId in $containerIds; do

	  	name=$(docker inspect -f {{.Name}} $containerId | tail -c +2) ;

	  	allMounts=$(docker inspect -f '{{range .Mounts}}{{.Source}} {{end}}' $containerId);

      totalAll="0"
      if [ "$allMounts" != "" ] ; then
          # totalAll=$(run_using_root du -sm --total $allMounts | tail -1 | awk '{print $1}')
          totalAll=$( du -sm --total $allMounts | tail -1 | awk '{print $1}')
      fi ;

		print_volume $totalAll $name

	done | sort --reverse --human-numeric-sort



	print_section "Volume Report per container"

	for containerId in $containerIds; do

	  	name=$(docker inspect -f {{.Name}} $containerId | tail -c +2) ;

	  	print_line "\n *$name: $containerId"

		mountArray=( $(docker inspect -f '{{range .Mounts}}{{.Source}} {{end}}' "$containerId") );

		if (( ${#mountArray[@]} == 0 )) ; then
			print_volume '-' '-'
		else
			for mount in "${mountArray[@]}"; do
				print_volume $(run_using_root du -hLs "$mount" 2>/dev/null | tail -n 1);
			done | sort --reverse --human-numeric-sort
		fi

	done

	# if [ "$USER" != "root" ]; then
	# 	print_line "Warning: Docker volume reports require root level access"
	# fi;

	# print_section "docker system df -v"
	run_and_format docker system df -v

}



function dmysql() {



	local imageName="${1:-mysql:5.7}" ;
	print_two_columns "imageName" "$imageName" ;


	local nameSuffix="from-dmysql" ;
	if [[ "$imageName" == "stop" ]] ; then
		print_command "Stopping mysql containers" \
			"$(docker stop $(docker ps -a | grep "$nameSuffix" | awk '{ print $1 }'))" ;
		return ;
	fi ;


	local containerName=$(echo "$imageName-$nameSuffix" | sed 's/:/-/g' | sed 's=/=-=g') ;
	print_two_columns "containerName" "$containerName" ;

	if docker ps -a | grep "$containerName" &>/dev/null ; then
		print_two_columns "network" "found existing $sqlNetwork" ;
		print_section "Found existing container - exiting. Use dmysql stop to clean up" ;
		return ;
	fi

	local sqlNetwork="${containerName}-network" ;

	if docker network ls | grep "$sqlNetwork" &>/dev/null ; then
		print_two_columns "network" "found existing $sqlNetwork" ;
	else
		print_command "Creating network" "$(docker network create -d bridge $sqlNetwork 2>&1)" ;
	fi



	# docker run -it  --name peter --rm -v $(pwd)/build:/transfer -p 9210:9210 the-docker-repo.yourcompany.com/csapplatform/test-utils:lates

	print_separator "Starting mysql"
	drun $containerName -p 3306:3306 -e MYSQL_ROOT_PASSWORD=nyw --network="$sqlNetwork" $imageName


	print_separator "Starting mysql admin"
	drun "$containerName-admin" -p 9080:80 -e PMA_HOST=$containerName --network="$sqlNetwork" phpmyadmin:latest

	print_separator "mysql containers started"
	print_two_columns "Admin" "http://$(hostname):9080"
}




#
# kubernetes
#
# alias mstart="clear;print_section minikube start --driver=vmware;minikube start --driver=vmware"
alias mks="clear;print_section minikube start;minikube start"
alias k=kubecolor
#if ! is_linux ; then
#  compdef kubecolor=kubectl
#  compdef k=kubectl
#fi;

function pods() {

	clear ;

	local namespace=${1:-all}

	resources "pods" "$namespace"
}

function deployments() {

	clear ;

	local namespace=${1:-all}

	resources "deployments" "$namespace"
}



function resources() {

	clear ;

	local attribute=$1 ;
	local namespace=$2 ;

	if [[ "$namespace" == "all" ]]; then
		namespace="--all-namespaces" ;
	else
		namespace="--namespace=$namespace" ;
	fi ;

	print_section "kubectl get $attribute $namespace"
	kubecolor get $attribute $namespace
}


function pdesc() {

	clear ;

	local podPattern=${1:-all};
	local logNamespace=${2:-all}

	targetPods=$(find_pod_names $podPattern) ;

	for targetPod in $(echo $targetPods) ; do

		echo pod $targetPod

		if [[ $logNamespace == "all" ]] ; then
			detectedNamespace=$(find_pod_namespace $targetPod)
			namespace="--namespace=$detectedNamespace" ;
		else
			namespace="--namespace=$logNamespace";
		fi ;


		print_section "kubectl describe pods $targetPod $namespace"
		kubecolor describe pods $targetPod $namespace

	done ;

}


function logs() {

	clear ;

	local podPattern=${1:-all};
	local logCount=${2:-20};
	local logNamespace=${3:-all};

	print_separator "podPattern logCount logNamespace"

	print_two_columns "podPattern" "$podPattern"
	print_two_columns "logCount" "$logCount (use follow to tail)"
	print_two_columns "logNamespace" "$logNamespace"

	targetPods="$(find_pod_names $podPattern)" ;

	for targetPod in $(echo $targetPods) ; do

		print_two_columns "targetPod" "$targetPod"

		if [[ $logNamespace == "all" ]] ; then
			detectedNamespace=$(find_pod_namespace $targetPod)
			namespace="--namespace=$detectedNamespace" ;
		else
			namespace="--namespace=$logNamespace";
		fi ;

		if [[ $logCount == "follow" ]] ; then
			logAmount="--follow" ;
		else
			logAmount="--tail=$logCount";
		fi ;


		print_section "kubectl logs $logAmount $targetPod $namespace"
		kubecolor logs $logAmount $targetPod $namespace

	done ;

}

function find_pod_namespace() {
	local targetPod=$1;
	kubectl get pods --all-namespaces | grep $targetPod | tail -1 | awk '{print $1}'
}

function find_pod_names() {

	podTarget=$1
	podNames=$(kubectl get pod --all-namespaces | grep "$podTarget" | awk '{print $2}')

	#
	# this function returns the name of the pod
	#
	echo "$podNames"
}


function gresource() {

	local attribute="$1"
	local output="${2:-}"

	print_section "kubectl get $attribute $output"
	kubecolor get $attribute $output
}





# function namespaces() {

# 	print_section "kubectl get namespaces"
# 	kubecolor get namespaces
# }



alias namespaces="gresource namespaces"
alias namespacesd="gresource namespaces --output=yaml"
alias events="gresource events"
alias eventsd="gresource events --output=yaml"
alias ls="ls --color=auto"
# alias p=pods




#
# azul
#		community release: azul <java version> --silent --ca
#		token contract: azul <java version>
#
azulUrl="https://api.azul.com/metadata/v1"
function azulApi() {

	local javaVersion=${1:-17}
	local verbose=${2:---silent}
	local token=${3:-e42aa97c6f1de0b2fa259d8d2a24346e0f144390}
	local command=${4:-/zulu/packages/}

	local authHeader="Authorization: Bearer $token"
	local availType="sa"
	if [[ "$token" == "--ca" ]] ; then
		authHeader=""
		availType="ca"
	fi

	print_two_columns "api docs" "https://api.azul.com/metadata/v1/docs/swagger"
	print_two_columns "authHeader" "$authHeader"
	print_separator "azul helper: $command"

	#
	# archive: [ deb, rpm, dmg, tar.gz, zip, cab, msi ]
	# libc: [ glibc, uclibc, musl ]
	# release_type: Critical Patch Update CPU, Patch Set Update PSU, or Limited Update LU.
	#
	#


	curl $verbose --get \
		"$azulUrl/$command" \
		\
		--header "$authHeader"    `# only needed for azul support releases sa` \
  	--header "accept: application/json" \
  	\
  	--data "java_version=$javaVersion" \
  	--data "latest=true" \
  	--data "java_package_type=jdk" \
  	--data "release_status=ga" \
  	--data "release_type=PSU" \
  	\
  	--data "page=1" --data "page_size=99" \
  	\
  	--data "availability_types=$availType" \
  	--data "os=linux-glibc" \
  	--data "arch=x64" \
  	--data "archive_type=tar.gz" \
  	--data "javafx_bundled=false" \
  	--data "crac_supported=false" \
  	\
  	| jq |  bat $batDefaultParameters



}

function showFunction() {
  local name=${1:-showFunction}
#  whence -f $name | bbat bash
  whence -f migrateToIntegrationSource | bat --theme="$batTheme" --language="bash" --paging=never
}

