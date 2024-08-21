# grep , grep a file for a pattern

# Notes:
#   - ref https://man7.org/linux/man-pages/man1/grep.1.html
#   - Set the filter as needed
#   - Tail/Grep commands can put significant load on VM, use CSAP vm monitor to observe impact
#

#
#  Also: file-tail template can be used
#

searchPatterns=("WARNING" "SEVERE" "your expression here" ) ;
maxMatches=100

location="_file_"

searchPatternAsGrepRegEx=$(buildGrepPatternFromArray "${searchPatterns[@]}" )
# searchCommand="--fixed-strings $stringToFind"
# --fixed-strings $stringToFind --regexp=$stringToFind"

options="" ; # --recursive --ignore-case --word-regexp
linesBefore=0
linesAfter=0

if test -d $location ; then options="--recursive"; fi ;

print_two_columns "search" "$searchPatternAsGrepRegEx"
totalMatchCount=$(eval grep  $options $searchPatternAsGrepRegEx $location | wc -l) ;
print_two_columns "view limit" "$maxMatches ( $totalMatchCount found )"
print_two_columns "location" "$location"
print_two_columns "options" "$options"
print_two_columns "linesBefore" "$linesBefore"
print_two_columns "linesAfter" "$linesAfter"

if ! test -r $location ; then print_section "no read access, try running using root"; fi ;

print_separator "output"

totalMatchCount=$(eval grep  $options $searchPatternAsGrepRegEx $location | wc -l) ;

eval grep  \
  --after-context=$linesAfter \
  --before-context=$linesBefore \
  --max-count=$maxMatches \
  $options $searchPatternAsGrepRegEx  \
  $location

#
# reduce multiple spaces to one: | sed -e's/  */ /g'
# exclude columns:               | cut --complement -d" " -f1,3,5
#


#grep -A $linesAfter -B $linesBefore -m $maxMatches $searchCommand "$filter" $location

# 
# zgrep -A $linesAfter -B $linesBefore -m $maxMatches -i "$filter" $location