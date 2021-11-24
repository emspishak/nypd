package emspishak.nypd.profilepayroll;

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

    List<String[]> profiles = readProfiles(profileFile);
    List<String[]> payroll = readPayroll(payrollFile);
  }

  private List<String[]> readProfiles(File profileFile) throws CsvException, IOException {
    CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(profileFile));
    return reader.readAll();
  }

  private List<String[]> readPayroll(File payrollFile) throws CsvException, IOException {
    CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(payrollFile));
    return reader.readAll();
  }
}
