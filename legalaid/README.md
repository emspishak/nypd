# Legal Aid documents

This downloads all documents associated with the Legal Aid organization on
DocumentCloud
(https://www.documentcloud.org/app?q=organization%3Athe-legal-aid-society-2723).
These documents include CCRB closing reports, DA letters, lawsuits and probably
more.


To run:

1. Install Bazel and Java
   (https://docs.bazel.build/versions/main/tutorial/java.html#before-you-begin)
1. Run (from the project root):

   ```
   bazel run //legalaid:LegalAid -- -output-dir /full/path/to/outputdir/
   ```
