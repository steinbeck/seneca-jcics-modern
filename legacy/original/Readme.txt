SENECA is a program package for Computer-Assisted Structure
Elucidation (CASE) of organic molecules.

Copyright for the SENECA package by Dr. Christoph Steinbeck (see
License.txt for more). 

1. Contact

Email:
steinbeck@ice.mpg.de

Postal Address:
Dr. Christoph Steinbeck
Max-Planck-Institut für Chemische Ökologie
Carl-Zeiss-Promenade 10
D-07745 Jena
German
Fax: +49 (0)3641 643665


1. Introduction

Please remember that this is a proof of concept implementation, which has not
been develop further since the publication of "SENECA - A Platform-Independent, 
Distributed, and Parallel System for Computer-Assisted Structure Elucidation in
Organic Chemistry. 

If, by any chance, the new SENECA-CDK has been released by now, please use this
new reimplementation, since it will be actively supported in the future. Check 
http://seneca.sourceforge.net for this new release.

2. Quickstart:

2.1 Installation

Unpack the zip file. It is very likely that you already did this.
Run seneca.bat or seneca.sh, depending whether you are on a MS Windows- or on
a Unix-based system. SENECA should start without problems. Otherwise edit the 
startup file (seneca.bat or seneca.sh) and uncomment the lines that reflect the
path of your java installation

2.2 Run a test

Load the sample dataset for Eurabidiol from the "sml" directory in the seneca
directory. 
Review the "General Settings" pane as well as the "NMR Spectra" Pane in order
to see what kind of information is provides. 
Go to the "Atom Properties" pane and press the "Auto Assignment" button.
The carbon resonances as well as hydrogen counts will be assigned to the heavy
atoms. You will notice a red warning, the the hydrogen sum of assigned hydrogens
differs from that to be exspected upon inspection of the molecular formula.
Assign the two missing hydrogens to the two oxygen atoms by manually editing the
hydrogen count columns of O-16 and O17. Do not continue unless the warning turns green.

Inspect the "Annealing Schedule" pane to see what is gives you. You can just 
leave the settings as they are. The only parameter that usually needs to be 
adjusted is the "Plateau Steps" parameter. It need to be increased for larger 
molecules.

Go to the "Judeges" Pane and press the "Autoconfigure" button in the "C,H-long
range Corr." and "Carbon Shifts" pane. 

Go to the "Structure Generation" pane and start four local servers by pressing
the "Start local server" button" four times. Mark all four local servers in the 
table and start the calculation by pressing the "Start as selected" button. Follow 
the progress of the calculation. It should be finished within a few minutes.  
