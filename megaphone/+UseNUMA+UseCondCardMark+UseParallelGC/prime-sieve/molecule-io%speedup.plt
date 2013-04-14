set title "prime-sieve:molecule-io - 24*amd64, 1.7.0_04-ea-b06, [+UseNUMA, +UseCondCardMark, +UseParallelGC]"
set xlabel "Threads"
set ylabel "Speedup"
set xtics 1
set log y
#set key out

#set term png
#set output "molecule-io.png"
#set term postscript eps enhanced
#set output "molecule-io.eps"

plot 'molecule-io_wcfj_SST=1-CCT=1.dat' using 1:4:($4*$3)/100 with errorlines title "wcfj:SST=1-CCT=1",\
'molecule-io_wcfj_SST=10-CCT=10.dat' using 1:4:($4*$3)/100 with errorlines title "wcfj:SST=10-CCT=10",\
'molecule-io_wcfj_SST=10-CCT=40.dat' using 1:4:($4*$3)/100 with errorlines title "wcfj:SST=10-CCT=40",\
'molecule-io_wcfj_SST=40-CCT=10.dat' using 1:4:($4*$3)/100 with errorlines title "wcfj:SST=40-CCT=10",\
'molecule-io_wcfj_SST=40-CCT=40.dat' using 1:4:($4*$3)/100 with errorlines title "wcfj:SST=40-CCT=40",\
'molecule-io_wcfj_SST=50-CCT=50.dat' using 1:4:($4*$3)/100 with errorlines title "wcfj:SST=50-CCT=50"
pause -1
