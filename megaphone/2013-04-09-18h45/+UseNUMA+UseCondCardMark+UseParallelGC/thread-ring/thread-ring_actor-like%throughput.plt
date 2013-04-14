set title "thread-ring:thread-ring:actor-like - 24*amd64, 1.7.0_04-ea-b06, [+UseNUMA, +UseCondCardMark, +UseParallelGC]"
set xlabel "Threads"
set ylabel "Throughput[msg/s]"
set xtics 1
#set key out

#set term png
#set output "thread-ring_actor-like.png"
#set term postscript eps enhanced
#set output "thread-ring_actor-like.eps"

plot 'molecule-core_actor-like.dat' using 1:4:($4*$3)/100 with errorlines title "molecule-core",\
'molecule-stream_actor-like.dat' using 1:4:($4*$3)/100 with errorlines title "molecule-stream",\
'molecule-word_actor-like.dat' using 1:4:($4*$3)/100 with errorlines title "molecule-word"
pause -1
