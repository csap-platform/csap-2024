# clearBuffers, show and clear linux memory buffers. du commands can cause buffers to fill up.




run_and_format free -h

run_and_format_root 'sync && echo 3 > /proc/sys/vm/drop_caches'

run_and_format free -h

run_and_format show_memory 10


# run_and_format ps aux --sort -%mem ' | head -10

