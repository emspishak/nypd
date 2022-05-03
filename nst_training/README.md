# Neighborhood Safety Team officers

This finds officers who are likely on a "Neighborhood Safety Team". This takes NYPD profile data
from https://nypdonline.org/link/2 (via https://github.com/ryanwatkins/nypd-officer-profiles/ ) and
finds officers that have taken the "NEIGHBORHOOD SAFETY TEAM TRAINING, 7-DAY COURSE" (for example,
see 2/23/2022 on https://oip.nypdonline.org/view/1/@TAXID=953536 ).
https://boltsmag.org/new-york-revives-notorious-plainclothes-police-squads/ mentions this course.

To run:

1. Install Bazel and Java
   (https://docs.bazel.build/versions/main/tutorial/java.html#before-you-begin)
1. Run (from the project root):

   ```
   bazel run //nst_training:NstTraining -- -profile-dir /full/path/to/nypd-officers-profiles -50a-data /full/path/to/50adata -output /full/path/to/output.csv
   ```
