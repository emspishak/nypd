# SRG officers

This looks at training courses taken by Strategic Response Group (SRG) officers and officers who
have taken SRG trainings. The goal is to try to figure out if there are SRG officers in non-SRG
commands. This takes NYPD profile data from https://nypdonline.org/link/2 (via
https://github.com/ryanwatkins/nypd-officer-profiles/ ).

To run:

1. Install Bazel and Java
   (https://docs.bazel.build/versions/main/tutorial/java.html#before-you-begin)
1. Run (from the project root):

   ```
   bazel run //srg_training:SrgTraining -- -profile-dir /full/path/to/nypd-officers-profiles -50a-data /full/path/to/50adata -output-dir /full/path/to/outputdir/
   ```
