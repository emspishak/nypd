package emspishak.nypd.profilepayroll;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/** Merges NYPD profile data with NYC payroll data. */
public final class ProfilePayroll {

  /**
   * "Police Department" job titles that won't ever match anyone in NYPD profile data becuase
   * they're civilian positions. This is not an exhaustive list, but just includes the most frequent
   * titles.
   */
  private static final ImmutableSet<String> TITLES_TO_REMOVE =
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

  /* The date format of dates in both the profile and payroll data. */
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy");

  /**
   * A map from tax id in the profile data, to borough in the payroll data. Used when officers have
   * the exact same name and start date.
   */
  private static final ImmutableMap<String, String> MANUAL_MATCHES =
      ImmutableMap.<String, String>builder()
          .put("939647", "MANHATTAN")
          .put("939646", "BRONX")
          .put("953293", "BRONX")
          .put("953294", "BROOKLYN")
          // This actually matches two officers, but they're almost exactly the same so this will
          // just choose the first one.
          .put("964716", "BROOKLYN")
          .put("970111", "QUEENS")
          .put("968062", "BROOKLYN")
          .put("968061", "MANHATTAN")
          .build();

  /** Suffixes to strip from names in payroll data because profile data doesn't include this. */
  private static final Pattern SUFFIXES = Pattern.compile(" ((JR(\\.)?)|II|III|IV)$");

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
    ArrayListMultimap<String, Payroll> payroll = readPayroll(payrollFile);

    merge(profiles, payroll);
  }

  private ImmutableList<Profile> readProfiles(File profileFile) throws CsvException, IOException {
    CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(profileFile));
    return reader.readAll().stream().map(Profile::new).collect(toImmutableList());
  }

  private ArrayListMultimap<String, Payroll> readPayroll(File payrollFile)
      throws CsvException, IOException {
    CSVReader reader = new CSVReader(new FileReader(payrollFile));
    List<String[]> unfiltered = reader.readAll();
    ArrayListMultimap<String, Payroll> filtered = ArrayListMultimap.create();

    for (String[] row : unfiltered) {
      Payroll payroll = new Payroll(row);
      // Only get payroll data from this year.
      if (!payroll.getYear().equals("2021")) {
        continue;
      }
      if (TITLES_TO_REMOVE.contains(payroll.getTitle())) {
        continue;
      }
      filtered.put(payroll.getLastName(), payroll);
    }

    return filtered;
  }

  // TODO: return the merged data.
  private void merge(ImmutableList<Profile> profiles, ArrayListMultimap<String, Payroll> payroll) {
    int matches = 0;

    for (Profile profile : profiles) {
      Payroll match = findMatch(profile, payroll.get(profile.getLastName()));
      if (match != null) {
        // Remove the match so it won't match anyone else.
        checkState(payroll.remove(profile.getLastName(), match), match);
        matches++;
      }
    }

    System.out.printf("matches: %s, total: %s%n", matches, profiles.size());
  }

  /** The payrolls parameter is a list of payroll data whose last name matches the given profile. */
  private Payroll findMatch(Profile profile, List<Payroll> payrolls) {
    // If we identified a manual match, go with that.
    if (MANUAL_MATCHES.containsKey(profile.getTaxId())) {
      return findManualMatch(profile, payrolls);
    }

    ImmutableList<Payroll> initialPayrolls = ImmutableList.copyOf(payrolls);

    List<Payroll> matches = new ArrayList<>();
    for (Payroll payroll : payrolls) {
      // Check if the first name matches (all elements in payrolls already have the same last name).
      if (profile.getFirstName().equals(payroll.getFirstName())) {
        matches.add(payroll);
      }
    }

    if (matches.isEmpty()) {
      // If there are no first name matches, try where the profile first name is a prefix of the
      // payroll first name.
      for (Payroll payroll : initialPayrolls) {
        if (payroll.getFirstName().startsWith(profile.getFirstName())) {
          matches.add(payroll);
        }
      }
      return findMatchAfterFirstName(profile, matches);
    } else if (matches.size() == 1) {
      return matches.get(0);
    }

    // If there are multiple first name matches, narrow them down with the middle initial.
    return findMatchAfterFirstName(profile, matches);
  }

  private Payroll findMatchAfterFirstName(Profile profile, List<Payroll> matchingFirstNames) {
    // If there are multiple first name matches, narrow them down with the middle initial.
    for (Iterator<Payroll> it = matchingFirstNames.iterator(); it.hasNext(); ) {
      Payroll payroll = it.next();
      if (!profile.getMiddleInitial().equals(payroll.getMiddleInitial())) {
        it.remove();
      }
    }
    if (matchingFirstNames.isEmpty()) {
      return null;
    } else if (matchingFirstNames.size() == 1) {
      return matchingFirstNames.get(0);
    }

    // If there are still multiple matches, narrow them down by appointment date.
    for (Iterator<Payroll> it = matchingFirstNames.iterator(); it.hasNext(); ) {
      Payroll payroll = it.next();
      if (!profile.getAppointmentDate().equals(payroll.getAppointmentDate())) {
        it.remove();
      }
    }
    if (matchingFirstNames.isEmpty()) {
      return null;
    } else if (matchingFirstNames.size() == 1) {
      return matchingFirstNames.get(0);
    }

    throw new IllegalStateException(
        "multiple matches found for " + profile + " - this is unhandled.");
  }

  private Payroll findManualMatch(Profile profile, List<Payroll> payrolls) {
    checkState(MANUAL_MATCHES.containsKey(profile.getTaxId()), profile);
    for (Payroll payroll : payrolls) {
      if (MANUAL_MATCHES.get(profile.getTaxId()).equals(payroll.getBorough())) {
        return payroll;
      }
    }
    throw new IllegalStateException("no manual match found for " + profile);
  }

  private static LocalDate parseDate(String date) {
    return LocalDate.parse(date, DATE_FORMAT);
  }

  private static class Profile {

    private final String[] rows;

    private Profile(String[] rows) {
      this.rows = rows;
    }

    private String getTaxId() {
      return rows[0];
    }

    private String getFirstName() {
      return rows[2];
    }

    private String getMiddleInitial() {
      return rows[4];
    }

    private String getLastName() {
      return rows[3];
    }

    private LocalDate getAppointmentDate() {
      return ProfilePayroll.parseDate(rows[8]);
    }

    private String[] getRaw() {
      return rows;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("tax id", getTaxId())
          .add("first name", getFirstName())
          .add("middle initial", getMiddleInitial())
          .add("last name", getLastName())
          .add("appointment date", getAppointmentDate())
          .toString();
    }
  }

  private static class Payroll {

    private static final CharMatcher VALID_NAME_CHARS = CharMatcher.inRange('A', 'Z');

    private final String[] rows;

    private Payroll(String[] rows) {
      this.rows = rows;
    }

    private String getFirstName() {
      return normalizeName(rows[4]);
    }

    private String getMiddleInitial() {
      return rows[5];
    }

    private String getLastName() {
      return normalizeName(SUFFIXES.matcher(rows[3]).replaceAll(""));
    }

    private String getTitle() {
      return rows[8];
    }

    private String getYear() {
      return rows[0];
    }

    private String getBorough() {
      return rows[7];
    }

    private LocalDate getAppointmentDate() {
      return ProfilePayroll.parseDate(rows[6]);
    }

    private String normalizeName(String name) {
      return VALID_NAME_CHARS.retainFrom(name);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("first name", getFirstName())
          .add("middle initial", getMiddleInitial())
          .add("last name", getLastName())
          .add("title", getTitle())
          .add("appointment date", getAppointmentDate())
          .toString();
    }
  }
}
