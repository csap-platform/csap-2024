#!/bin/bash


LINE_WIDTH=${LINEWIDTH:-120}
LINE="$(printf -- '_%.0s' $(seq 1 $LINE_WIDTH))\n" ;
#
#   prints
#
function print_with_big_head() {
	print_line "\n\n\n\n" ;
	print_with_head $*
}

function print_with_head() {
	echo -e "\n$LINE \n$*\n$LINE";
}


function print_section() {
#	echo -e  "\n* \n**\n*** \n**** \n*****  $* \n**** \n*** \n** \n*"
	echo -e  "\n** \n**  $* \n**\n\t"
}


function print_block() {
#	echo -e  "\n* \n**\n*** \n**** \n*****  $* \n**** \n*** \n** \n*"
	echo -e  "\n\n## \n##  ==> $* \n##\n"
}

function print_mainsection() {
#	echo -e  "\n* \n**\n*** \n**** \n*****  $* \n**** \n*** \n** \n*"
	echo -e  "\n\n* \n** $( print_sep_width 30 $* )  \n*"
}

function print_on_error() {
  if (( $1 != 0 )) ; then
    shift
	  echo -e  "\n\n==>  $*"
  fi
}

function print_subsection() {
#	echo -e  "\n* \n**\n*** \n**** \n*****  $* \n**** \n*** \n** \n*"
	echo -e  "\n\n==>  $*"
}
function print_subsection2() {
#	echo -e  "\n* \n**\n*** \n**** \n*****  $* \n**** \n*** \n** \n*"
	echo -e  "\n\t---  $*"
}

function print_super() {
	echo -e  "\n\n\n\n\n\n\n\n$(printf -- '*%.0s' $(seq 1 $LINE_WIDTH)) \n* \n**\n*** \n**** \n***** \n*****  $* \n***** \n**** \n*** \n** \n*\n$(printf -- '*%.0s' $(seq 1 $LINE_WIDTH)) \n\n"
}

function print_with_head2() {
	>&2 echo -e "\n$LINE \n$* \n$LINE";
}

function print_command() {

	local description="$1" ;
	shift 1 ;
	local commandOutput="$*" ;

	echo -e "\n\n$description:\n$LINE \n$commandOutput\n$LINE\n";

}
function print_contents() {

	local description="$1" ;
	shift 1 ;
	local commandOutput="$*" ;

	echo -e "\n\n$description:"
	if [[ "$commandOutput" == "" ]] ; then
	  echo -e "--- EMPTY ---\n"
  else
    echo -e  "--- START --- \n$commandOutput\n--- END ---\n"
  fi

}

function print_and_run() {

	local description="$*" ;
#	echo "$description" | sed 's/--/\\\n\t--/g'
  print_line ""
  echo "$description" | sed 's/ / \\\n\t /g' >&2
  print_separator "output" >&2

  local timerCommand=""
  if [[ "$csapShellTimer" == "true" ]] ; then
    timerCommand='\time -l'
    timerCommand='\time'
  fi
  eval "$timerCommand" $description
}

function setTimer() {
  local use=${1:-true} ;
  csapShellTimer="$use"
}


function print_error() {
	>&2 echo -e "\n$LINE \n ERROR:    $* \n$LINE";
}

function delay_with_message() {

	local seconds=${1:-10} ;
	local message=${2:-continuing in};
	local dots;
	local iteration;
	local sleepDuration=1;
	local maxDots=80 ;

	print_line "\n\n $message \n"
	for (( iteration=$seconds; iteration > 0; iteration--)) ; do

		if (( $iteration < $maxDots )) ; then
			maxDots=$iteration;
		fi ;

		dots=$(printf "%0.s-" $( seq 1 1 $maxDots ));
		print_line "$(printf "%3s" $iteration) seconds  $dots"  ;


		if (( $iteration > 90)) ; then
			sleepDuration=30 ;
		elif (( $iteration > 30)) ; then
			sleepDuration=10;
		elif (( $iteration > 10)) ; then
			sleepDuration=3;
		else
			sleepDuration=1;
		fi ;

		sleep $sleepDuration;
		iteration=$(( $iteration - $sleepDuration )) ;
	done

}

function csapFqdn() {
	myHostName=$(hostname --long 2>&1) ;
	if (( $? != 0 )) ; then myHostName=$(hostname -f).local ; fi ;
	echo $myHostName;
}

function print_with_date() {
		print_if_debug "System millis: $(date '+%N ms')"
		printf "\n\n %-20s %-30s %s\n\n" "$(date '+%x %H:%M:%S')"  "$(whoami)@$(csapFqdn)" "$*";

	#echo -e "$LINE \n $(date '+%x %H:%M:%S') host: '$HOSTNAME' user: '$USER' \n $* \n$LINE";
}

function print_line() {
	echo -e "   $*" ;
}

function print_sep_width() {

  local customWidth=${1}
  shift

	local theMessage="   $*   " ;
	local dashesWidth=$(($customWidth - ${#theMessage} )) ;
	dashesWidth=$(($dashesWidth/ 2)) ;
	if (( $dashesWidth < 5 )) ; then
		dashesWidth=5;
	fi ;

	local lineCharacters="$(printf -- '-%.0s' $(seq 1 $dashesWidth))" ;

	printf '%*.*s %s %*.*s' 0 "$dashesWidth" "$lineCharacters" "$theMessage"  0 "$dashesWidth" "$lineCharacters"
}

function print_separator() {
	# echo -e "\n\n---------------   $*  ------------------" ;
	#printf "\n\n---------------   %-40s  ------------------\n" $*;

	local theMessage="   $*   " ;
	local dashesWidth=$(($LINE_WIDTH - ${#theMessage} )) ;
	dashesWidth=$(($dashesWidth/ 2)) ;
	if (( $dashesWidth < 5 )) ; then
		dashesWidth=5;
	fi ;

	#echo "theMessage width: ${#theMessage} dashesWidth: $dashesWidth" ;

	#local lineCharacters="$(printf '%0.1s' -{1..100})" ;
	local lineCharacters="$(printf -- '-%.0s' $(seq 1 $dashesWidth))" ;

	printf '\n\n%*.*s %s %*.*s\n' 0 "$dashesWidth" "$lineCharacters" "$theMessage"  0 "$dashesWidth" "$lineCharacters"
}

function print_separator2() {
	>&2 echo -e "\n\n---------------   $*  ------------------" ;
}

function print_line2 {
	>&2 echo -e "   $*" ;
}

# function test() { >&2 echo hi ; }
function print_columns() {
	printf "%15s: %-20s %15s: %-20s %15s: %-20s \n" "$@" ;
}

function print_two_columns() {
	printf "%25s: %-20s\n" "$@";
}
function print_3_columns() {
	printf "%25s: %-20s %s\n" "$@";
}

function print_two_columns2() {
	>&2 printf "%25s: %-20s\n" "$@";
}

function print_info() {
	local leadWidth=${3:-30s};
	printf "%-$leadWidth: %s\n" "$1" "$2";
}

function print_if_debug() {

	if [[ "$debug"  != "" ]] ; then
		printf "%-22s %s\n\n" "DEBUG: $(date '+%Nms')" "$*";
		#echo `date "+%Nms"`;echo -e "$*" ; echo
	fi ;
}

function print_debug_command() {

	if [[ "$debug"  != "" ]] ; then

		local description="$1" ;
		shift 1 ;
		local commandOutput="$*" ;

		echo -e "\n\nDEBUG: $description:\n$LINE \n$commandOutput\n$LINE\n";
	fi

}


function print_zarray_in_columns() {
  local description="$1" ;
  shift
  local contents=($*)
  print_separator "$description"
  for item in $contents; do
    print_line $item
  done
}
#
# usage "description" "array variable name" <column-width> <row-width>
#
function print_array_in_columns() {

    if [[ "${ZSH_VERSION}" != "" ]] ; then
      print_error "print_array_in_columns" does not work in zsh
      return 99
    fi

    local description="$1" ;
    local arrayName="$2[@]" # parameter expansion
    local arrayContents
    if is_linux ; then
      arrayContents=("${!arrayName}")
    else
      arrayName="$2"
      #print_two_columns "arrayName" "$arrayName"
      arrayContents=${(P)${arrayName}}
      if (( ${#arrayContents[@]} == 0 )) ; then
        print_line "   $description"
        return
      fi ;
      #print_two_columns "arrayContents" "$arrayContents"
      # return
    fi ;

    local columnWidth="${3:-30}" ;
    local rowWidth="${4:-180}" ;
#    print_two_columns "columnWidth" "'$columnWidth'"
#    print_two_columns "rowWidth" "'$rowWidth'"


    local pathFormat="%.${columnWidth}s      \n" ;
    local separator='     '
    #--output-separator, --table is linux only
#    print_command "$description" \
#      "$( printf "$pathFormat" "${arrayContents[@]}" | column   -c $rowWidth  | column -s "$separator" -t)"

    print_separator "$description"
    printf "$pathFormat" "${arrayContents[@]}" | column   -c $rowWidth  | column -s "$separator" -t

}



function print_with_prompt() {

	print_section $*
	#print_separator $*

	if $isPrompt ; then
		print_line "enter to continue or ctrl-c to exit"
		read progress
	fi ;
}

function prompt_to_continue() {

	print_with_head $*
	#print_separator $*

	print_separator "enter y to continue, or anything else to abort"
	read -n 1 -r userResponse

	if [[ "$userResponse" != "y" ]] ; then
		print_line "Exiting '$progress'" ;
		exit 99 ;
	fi ;
}



function confirmation_prompt() {

	print_with_head $*
	#print_separator $*

	print_separator "enter y to continue, or anything else to abort"

	if is_linux ; then
		read -n 1 userResponse
	else
		#max os
		read -n userResponse
	fi ;

	#echo "userResponse: '$userResponse'"

	if [[ "$userResponse" == y* ]] ; then
		return 0 ;
	else
		return 99 ;
	fi ;
}


function print_if_verbose() {

	local verbose="$1" ;
	local heading="$2" ;
	local message="$3" ;

	if $verbose ; then
		print_two_columns "$heading" "$message"
	fi ;

}
