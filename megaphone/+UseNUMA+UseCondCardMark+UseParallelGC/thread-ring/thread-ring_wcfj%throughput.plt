set title "thread-ring:thread-ring:wcfj - 24*amd64, 1.7.0_04-ea-b06, [+UseNUMA, +UseCondCardMark, +UseParallelGC]"
set xlabel "Threads"
set ylabel "Throughput[msg/s]"
set xtics 1
#set key out

#set term png
#set output "thread-ring_wcfj.png"
#set term postscript eps enhanced
#set output "thread-ring_wcfj.eps"

plot 'molecule-core_wcfj.dat' using 1:4:($4*$3)/100 with errorlines title "molecule-core",\
'molecule-stream_wcfj.dat' using 1:4:($4*$3)/100 with errorlines title "molecule-stream",\
'molecule-word_wcfj.dat' using 1:4:($4*$3)/100 with errorlines title "molecule-word"
pause -1
