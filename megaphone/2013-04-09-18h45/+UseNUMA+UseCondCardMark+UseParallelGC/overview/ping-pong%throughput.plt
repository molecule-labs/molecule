set title "ping-pong - 24*amd64, 1.7.0_04-ea-b06, [+UseNUMA, +UseCondCardMark, +UseParallelGC]"
set xlabel "Threads"
set ylabel "Throughput[msg/s]"
set xtics 1
#set key out

#set term png
#set output "ping-pong.png"
#set term postscript eps enhanced
#set output "ping-pong.eps"

plot '../ping-pong/molecule-io_wcfj.dat' using 1:4:($4*$3)/100 with errorlines title "molecule-io:wcfj",\
'../ping-pong/scala-actors_fj.dat' using 1:4:($4*$3)/100 with errorlines title "scala-actors:fj"
pause -1
