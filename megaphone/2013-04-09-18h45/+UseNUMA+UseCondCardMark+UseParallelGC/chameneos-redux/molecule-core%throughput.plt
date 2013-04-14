set title "chameneos-redux:molecule-core - 24*amd64, 1.7.0_04-ea-b06, [+UseNUMA, +UseCondCardMark, +UseParallelGC]"
set xlabel "Threads"
set ylabel "Throughput[meetings/s]"
set xtics 1
#set key out

#set term png
#set output "molecule-core.png"
#set term postscript eps enhanced
#set output "molecule-core.eps"

plot 'molecule-core_wcfj_SST=0.dat' using 1:4:($4*$3)/100 with errorlines title "wcfj:SST=0",\
'molecule-core_wcfj_SST=1.dat' using 1:4:($4*$3)/100 with errorlines title "wcfj:SST=1",\
'molecule-core_wcfj_SST=2.dat' using 1:4:($4*$3)/100 with errorlines title "wcfj:SST=2",\
'molecule-core_wcfj_SST=6.dat' using 1:4:($4*$3)/100 with errorlines title "wcfj:SST=6",\
'molecule-core_wcfj_SST=12.dat' using 1:4:($4*$3)/100 with errorlines title "wcfj:SST=12",\
'molecule-core_wcfj_SST=24.dat' using 1:4:($4*$3)/100 with errorlines title "wcfj:SST=24",\
'molecule-core_wcfj_SST=48.dat' using 1:4:($4*$3)/100 with errorlines title "wcfj:SST=48"
pause -1
