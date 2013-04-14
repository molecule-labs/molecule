set title "chameneos-redux:molecule-io - 24*amd64, 1.7.0_04-ea-b06, [+UseNUMA, +UseCondCardMark, +UseParallelGC]"
set xlabel "Threads"
set ylabel "Speedup"
set xtics 1
#set key out

#set term png
#set output "molecule-io.png"
#set term postscript eps enhanced
#set output "molecule-io.eps"

plot 'molecule-io_wcfj_SST=0.dat' using 1:5:($5*$3)/100 with errorlines title "wcfj:SST=0",\
'molecule-io_wcfj_SST=1.dat' using 1:5:($5*$3)/100 with errorlines title "wcfj:SST=1",\
'molecule-io_wcfj_SST=2.dat' using 1:5:($5*$3)/100 with errorlines title "wcfj:SST=2",\
'molecule-io_wcfj_SST=6.dat' using 1:5:($5*$3)/100 with errorlines title "wcfj:SST=6",\
'molecule-io_wcfj_SST=12.dat' using 1:5:($5*$3)/100 with errorlines title "wcfj:SST=12",\
'molecule-io_wcfj_SST=24.dat' using 1:5:($5*$3)/100 with errorlines title "wcfj:SST=24",\
'molecule-io_wcfj_SST=48.dat' using 1:5:($5*$3)/100 with errorlines title "wcfj:SST=48"
pause -1
