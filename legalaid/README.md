# Legal Aid documents

This generates links to all documents that look like CCRB Closing Reports that
Legal Aid has uploaded to their DocumentCloud:
https://www.documentcloud.org/app?q=%2Borganization%3Athe-legal-aid-society-2723%20%22ccrb%20investigative%20recommendation%22%20%22case%20summary%22


To run:

1. Install Bazel and Java
   (https://docs.bazel.build/versions/main/tutorial/java.html#before-you-begin)
1. Run (from the project root):

   ```
   bazel run //legalaid:LegalAid
   ```
