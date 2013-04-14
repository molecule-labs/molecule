set title "thread-ring - 24*amd64, 1.7.0_04-ea-b06"
set xlabel "Threads"
set ylabel "Throughput[msg/s]"
set xtics 1
#set key out

set term png
set output "thread-ring%throughput.png"
#set term postscript eps enhanced
#set output "thread-ring%throughput.eps"

plot '../thread-ring/molecule-word_wcfj.dat' using 1:4:($4*$3)/100 with errorlines title "molecule-word:wcfj",\
'../thread-ring/scala-actors_fj.dat' using 1:4:($4*$3)/100 with errorlines title "scala-actors:fj"
pause -1
