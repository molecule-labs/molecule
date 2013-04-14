set title "chameneos-redux - 24*amd64, 1.7.0_04-ea-b06, [+UseNUMA, +UseCondCardMark, +UseParallelGC]"
set xlabel "Threads"
set ylabel "Speedup"
set xtics 1
#set key out

#set term png
#set output "chameneos-redux.png"
#set term postscript eps enhanced
#set output "chameneos-redux.eps"

plot '../chameneos-redux/molecule-core_wcfj_SST=0.dat' using 1:5:($5*$3)/100 with errorlines title "molecule-core:wcfj:SST=0",\
'../chameneos-redux/molecule-io_actor-like_SST=0.dat' using 1:5:($5*$3)/100 with errorlines title "molecule-io:actor-like:SST=0",\
'../chameneos-redux/molecule-io_fpfj_SST=0.dat' using 1:5:($5*$3)/100 with errorlines title "molecule-io:fpfj:SST=0",\
'../chameneos-redux/molecule-io_wcfj_SST=0.dat' using 1:5:($5*$3)/100 with errorlines title "molecule-io:wcfj:SST=0",\
'../chameneos-redux/scala-actors_fj.dat' using 1:5:($5*$3)/100 with errorlines title "scala-actors:fj"
pause -1
