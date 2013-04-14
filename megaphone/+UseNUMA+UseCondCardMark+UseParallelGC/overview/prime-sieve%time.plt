set title "prime-sieve - 24*amd64, 1.7.0_04-ea-b06"
set xlabel "Threads"
set ylabel "Time[s]"
set xtics 1
set log y
#set key out

set term png
set output "prime-sieve%time.png"
#set term postscript eps enhanced
#set output "prime-sieve%time.eps"

plot '../prime-sieve/molecule-io_actor-like_SST=1-CCT=1.dat' using 1:2:($2*$3)/100 with errorlines title "molecule-io:actor-like:SST=1-CCT=1",\
'../prime-sieve/molecule-io_actor-like_SST=50-CCT=50.dat' using 1:2:($2*$3)/100 with errorlines title "molecule-io:actor-like:SST=50-CCT=50",\
'../prime-sieve/molecule-core_actor-like_SST=50-CCT=50.dat' using 1:2:($2*$3)/100 with errorlines title "molecule-core:actor-like:SST=50-CCT=50",\
'../prime-sieve/molecule-io_fpfj_SST=1-CCT=1.dat' using 1:2:($2*$3)/100 with errorlines title "molecule-io:fpfj:SST=1-CCT=1",\
'../prime-sieve/molecule-io_fpfj_SST=50-CCT=50.dat' using 1:2:($2*$3)/100 with errorlines title "molecule-io:fpfj:SST=50-CCT=50",\
'../prime-sieve/molecule-core_fpfj_SST=50-CCT=50.dat' using 1:2:($2*$3)/100 with errorlines title "molecule-core:fpfj:SST=50-CCT=50",\
'../prime-sieve/molecule-io_wcfj_SST=1-CCT=1.dat' using 1:2:($2*$3)/100 with errorlines title "molecule-io:wcfj:SST=1-CCT=1",\
'../prime-sieve/molecule-io_wcfj_SST=50-CCT=50.dat' using 1:2:($2*$3)/100 with errorlines title "molecule-io:wcfj:SST=50-CCT=50",\
'../prime-sieve/molecule-core_wcfj_SST=50-CCT=50.dat' using 1:2:($2*$3)/100 with errorlines title "molecule-core:wcfj:SST=50-CCT=50",\
'../prime-sieve/scala-actors_fj.dat' using 1:2:($2*$3)/100 with errorlines title "scala-actors:fj"
pause -1
