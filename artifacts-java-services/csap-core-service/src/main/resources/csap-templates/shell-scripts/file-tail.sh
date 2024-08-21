# tail , tail a file with grep support


# Notes:
# 1. Set the filter as needed
# 2. Tail/Grep commands can put significant load on VM, use CSAP vm monitor to observe impact
# 3. stdbuf is used to prevent grep from buffering output. ref. http://www.pixelbeat.org/programming/stdio_buffering/

searchPatterns=("WARNING" "SEVERE" "your expression here" ) ;

location="_file_"

searchPatternAsGrepRegEx=$(buildGrepPatternFromArray "${searchPatterns[@]}" )

options="" ; # --ignore-case --word-regexp
linesBeforeMatch=1
linesAfterMatch=1
initialTailLines=100


print_two_columns "search" "$searchPatternAsGrepRegEx"
print_two_columns "location" "$location"
print_two_columns "initialTailLines" "$initialTailLines"
print_two_columns "linesBeforeMatch" "$linesBeforeMatch"
print_two_columns "linesAfterMatch" "$linesAfterMatch"
print_two_columns "options" "$options"

print_separator "output"
tail -F $location --lines=$initialTailLines \
  | eval grep  \
  --line-buffered \
  --after-context=$linesAfterMatch \
  --before-context=$linesBeforeMatch \
  $options $searchPatternAsGrepRegEx
