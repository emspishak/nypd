package emspishak.nypd.profilepayroll;

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.toCollection;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ObjectArrays;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
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
          // This actually matches two officers, but they're almost exactly the same so this will
          // just choose the first one.
          .put("964716", "BROOKLYN")
          .put("970111", "QUEENS")
          .put("968062", "BROOKLYN")
          .put("968061", "MANHATTAN")
          .put("965460", "MANHATTAN")
          .put("949549", "QUEENS")
          .build();

  /** Suffixes to strip from names in payroll data because profile data doesn't include this. */
  private static final Pattern SUFFIXES = Pattern.compile(" ((JR(\\.)?)|II|III|IV)$");

  @Option(name = "-profile", usage = "NYPD CSV profile data.")
  private File profileFile;

  @Option(name = "-payroll", usage = "NYC CSV payroll data for NYPD.")
  private File payrollFile;

  @Option(name = "-output", usage = "File to output the merged data as CSV.")
  private File outputFile;

  public static void main(String[] args) throws CmdLineException, CsvException, IOException {
    new ProfilePayroll().doMain(args);
  }

  private void doMain(String[] args) throws CmdLineException, CsvException, IOException {
    CmdLineParser parser = new CmdLineParser(this);
    parser.parseArgument(args);

    List<Profile> profiles = readProfiles(profileFile);
    ArrayListMultimap<String, Payroll> payroll = readPayroll(payrollFile);

    int totalProfiles = profiles.size();

    List<Merged> merged = merge(profiles, payroll);

    output(merged, profiles);

    System.out.printf(
        "merged %s out of %s profiles (%s unmerged)%n",
        merged.size(), totalProfiles, totalProfiles - merged.size());
  }

  private List<Profile> readProfiles(File profileFile) throws CsvException, IOException {
    CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(profileFile));
    return reader.readAll().stream().map(Profile::new).collect(toCollection(ArrayList::new));
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
      // Payroll data has a bunch of entries with no names which we can't do anything with.
      if (payroll.getFirstName().isEmpty() && payroll.getLastName().isEmpty()) {
        continue;
      }
      filtered.put(payroll.getLastName(), payroll);
    }

    return filtered;
  }

  private List<Merged> merge(List<Profile> profiles, ArrayListMultimap<String, Payroll> payroll) {
    List merged = merge(profiles, payroll, this::findLastNameMatches);

    // Do it all again! Now that there are fewer payroll options to match against we may hit some
    // new matches, especially with duplicate names and missing middle names. Example:
    //
    // In profile data:
    // TORRES, VICTOR J
    // TORRES, VICTOR M
    //
    // In payroll data
    // TORRES,VICTOR
    // TORRES,VICTOR,M
    //
    // The first round Victor J wouldn't match anything, but the second round Victor M would be gone
    // from payroll matches so there'd only be one Victor Torres and it would match.
    merged.addAll(merge(profiles, payroll, this::findLastNameMatches));

    // Try it one more time with all of the remaining profiles, but this time matching with prefixes
    // of last name.
    merged.addAll(merge(profiles, payroll, this::findLastNamePrefixMatches));

    return merged;
  }

  private List<Merged> merge(
      List<Profile> profiles,
      ArrayListMultimap<String, Payroll> payroll,
      BiFunction<Profile, ArrayListMultimap<String, Payroll>, List<Payroll>> lastNamesFunction) {
    List<Merged> merged = new ArrayList<>();

    for (Iterator<Profile> it = profiles.iterator(); it.hasNext(); ) {
      Profile profile = it.next();
      Payroll match = findMatch(profile, lastNamesFunction.apply(profile, payroll));
      if (match != null) {
        // Remove the match so it won't match anyone else.
        checkState(payroll.remove(match.getLastName(), match), match);
        // Remove the profile since we don't want to try to match it again in future round(s).
        it.remove();

        merged.add(new Merged(profile, match));
      }
    }

    return merged;
  }

  private List<Payroll> findLastNameMatches(
      Profile profile, ArrayListMultimap<String, Payroll> payroll) {
    return payroll.get(profile.getLastName());
  }

  private List<Payroll> findLastNamePrefixMatches(
      Profile profile, ArrayListMultimap<String, Payroll> payroll) {
    List<Payroll> matches = new ArrayList<>();
    for (Map.Entry<String, Payroll> entry : payroll.entries()) {
      String payrollLastName = entry.getKey();

      if (profile.getLastName().startsWith(payrollLastName)
          || payrollLastName.startsWith(profile.getLastName())) {
        matches.add(entry.getValue());
      }
    }
    return matches;
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
      // If there are no first name matches, try where one name is a prefix of the other.
      for (Payroll payroll : initialPayrolls) {
        if (payroll.getFirstName().startsWith(profile.getFirstName())
            || profile.getFirstName().startsWith(payroll.getFirstName())) {
          matches.add(payroll);
        }
      }

      if (matches.isEmpty()) {
        return null;
      } else if (matches.size() == 1) {
        return matches.get(0);
      }

      return findMatchAfterFirstName(profile, matches);
    } else if (matches.size() == 1) {
      return matches.get(0);
    }

    // If there are multiple first name matches, narrow them down with the middle initial.
    return findMatchAfterFirstName(profile, matches);
  }

  private Payroll findMatchAfterFirstName(Profile profile, List<Payroll> matchingFirstNames) {
    ImmutableList<Payroll> initialMatchingFirstNames = ImmutableList.copyOf(matchingFirstNames);

    // If there are multiple first name matches, narrow them down with the middle initial.
    for (Iterator<Payroll> it = matchingFirstNames.iterator(); it.hasNext(); ) {
      Payroll payroll = it.next();
      if (!profile.getMiddleInitial().equals(payroll.getMiddleInitial())) {
        it.remove();
      }
    }
    if (matchingFirstNames.isEmpty()) {
      // If no middle initials match, keep going to see if start dates match.
      matchingFirstNames = new ArrayList<>(initialMatchingFirstNames);
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
        "multiple matches found for " + profile + " - this is unhandled: " + matchingFirstNames);
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

  private void output(List<Merged> merged, List<Profile> profiles) throws IOException {
    CSVWriter writer = new CSVWriter(new FileWriter(outputFile));
    merged.stream().map(Merged::getRows).forEach(writer::writeNext);
    profiles.stream().map(Profile::getRaw).forEach(writer::writeNext);
    writer.close();
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

    private String[] getRaw() {
      return rows;
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

  private static final class Merged {

    private final Profile profile;
    private final Payroll payroll;

    private Merged(Profile profile, Payroll payroll) {
      this.profile = profile;
      this.payroll = payroll;
    }

    private String[] getRows() {
      return ObjectArrays.concat(profile.getRaw(), payroll.getRaw(), String.class);
    }
  }
}
