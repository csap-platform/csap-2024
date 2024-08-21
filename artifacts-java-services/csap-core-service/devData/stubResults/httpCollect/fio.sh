#
# test: randomReadWrite
#
     threads  blockSizeKb     readIops readBwMbPerS       readNs    writeIops  writeBwMbPs      writeNs  diskReadIos diskWriteIos    timeInSec  totalDiskGb
           1            4     12760.89        51043       847582      5462.42        21849       816030       139406        29052         10.8            1
           1            8      9050.77        72406       993725      3885.07        31080      1490100       183659        55069         20.2            1
           2            4     26480.85       105923     67923161     11396.87        45587     56107947       275365        79222         10.1            1
           2            8     23843.23       190745     20241231     10276.25        82209     17928965       252994        74972         10.5            1
