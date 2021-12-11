# Merges NYPD profile and payroll data

This takes NYPD profile data from https://nypdonline.org/link/2 (via https://github.com/ryanwatkins/nypd-officer-profiles/blob/main/officers.csv) and NYC payroll data from https://data.cityofnewyork.us/City-Government/Citywide-Payroll-Data-Fiscal-Year-/k397-673e (via https://github.com/ryanwatkins/nyc-nypd-citywide-payroll) and merges them together so you can see payroll data for officers based on rank/precinct/etc.

To run:

1. Install Bazel and Java (https://docs.bazel.build/versions/main/tutorial/java.html#before-you-begin)
1. Run (from the project root):

   ```
   bazel run //profile_payroll:ProfilePayroll -- -profile=/full/path/to/officers.csv -payroll=/full/path/to/nyc-nypd-citywide-payroll.csv -output-dir=/full/path/to/output/
   ```
