package emspishak.nypd.profilepayroll;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class ProfilePayroll {

  private final ImmutableSet<String> TITLES_TO_REMOVE =
      ImmutableSet.of(
          "ASSOCIATE TRAFFIC ENFORCEMENT AGENT",
          "AUTO MECHANIC",
          "CITY CUSTODIAL ASSISTANT",
          "COMPUTER ASSOCIATE",
          "CRIMINALIST",
          "EVIDENCE AND PROPERTY CONTROL SPECIALIST",
          "POLICE ADMINISTRATIVE AIDE",
          "POLICE CADET",
          "POLICE COMMUNICATIONS TECHNICIAN",
          "PRINCIPAL ADMINISTRATIVE ASSOCIATE -  NON SUPVR",
          "RADIO REPAIR MECHANIC",
          "SCHOOL CROSSING GUARD",
          "SCHOOL SAFETY AGENT",
          "SENIOR POLICE ADMINISTRATIVE AIDE",
          "SUPERVISING POLICE COMMUNICATIONS TECHNICIAN",
          "SUPERVISOR OF SCHOOL SECURITY",
          "TRAFFIC ENFORCEMENT AGENT");

  @Option(name = "-profile", usage = "NYPD CSV profile data.")
  private File profileFile;

  @Option(name = "-payroll", usage = "NYC CSV payroll data for NYPD.")
  private File payrollFile;

  public static void main(String[] args) throws CmdLineException, CsvException, IOException {
    new ProfilePayroll().doMain(args);
  }

  private void doMain(String[] args) throws CmdLineException, CsvException, IOException {
    CmdLineParser parser = new CmdLineParser(this);
    parser.parseArgument(args);

    ImmutableList<Profile> profiles = readProfiles(profileFile);
    ImmutableListMultimap<String, Payroll> payroll = readPayroll(payrollFile);
  }

  private ImmutableList<Profile> readProfiles(File profileFile) throws CsvException, IOException {
    CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(profileFile));
    return reader.readAll().stream().map(Profile::new).collect(toImmutableList());
  }

  private ImmutableListMultimap<String, Payroll> readPayroll(File payrollFile)
      throws CsvException, IOException {
    CSVReader reader = new CSVReader(new FileReader(payrollFile));
    List<String[]> unfiltered = reader.readAll();
    ImmutableListMultimap.Builder<String, Payroll> filtered = ImmutableListMultimap.builder();

    for (String[] row : unfiltered) {
      Payroll payroll = new Payroll(row);
      if (!payroll.getYear().equals("2021")) {
        continue;
      }
      if (TITLES_TO_REMOVE.contains(payroll.getTitle())) {
        continue;
      }
      filtered.put(payroll.getLastName(), payroll);
    }

    return filtered.build();
  }

  private static class Profile {

    private final String[] rows;

    private Profile(String[] rows) {
      this.rows = rows;
    }

    private String getFirstName() {
      return rows[2];
    }

    private String getLastName() {
      return rows[3];
    }

    private String[] getRaw() {
      return rows;
    }
  }

  private static class Payroll {

    private final String[] rows;

    private Payroll(String[] rows) {
      this.rows = rows;
    }

    private String getFirstName() {
      return rows[4];
    }

    private String getLastName() {
      return rows[3];
    }

    private String getTitle() {
      return rows[8];
    }

    private String getYear() {
      return rows[0];
    }
  }
}
