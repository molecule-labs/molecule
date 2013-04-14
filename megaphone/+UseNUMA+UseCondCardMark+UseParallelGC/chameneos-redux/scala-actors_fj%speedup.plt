set title "chameneos-redux:scala-actors:fj - 24*amd64, 1.7.0_04-ea-b06, [+UseNUMA, +UseCondCardMark, +UseParallelGC]"
set xlabel "Threads"
set ylabel "Speedup"
set xtics 1
#set key out

#set term png
#set output "scala-actors_fj.png"
#set term postscript eps enhanced
#set output "scala-actors_fj.eps"

plot 'scala-actors_fj.dat' using 1:5:($5*$3)/100 with errorlines title ""
pause -1
