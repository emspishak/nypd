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
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
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
          "*ADM SCHOOL SECURITY MANAGER-U",
          "*AGENCY ATTORNEY",
          "*ASSISTANT ADVOCATE-PD",
          "*ATTORNEY AT LAW",
          "*CERTIFIED LOCAL AREA NETWORK ADMINISTRATOR",
          "*CUSTODIAL ASSISTANT",
          "ACCOUNTANT",
          "ADM MANAGER-NON-MGRL",
          "ADMIN COMMUNITY RELATIONS SPECIALIST",
          "ADMIN CONSTRUCTION PROJECT MANAGER",
          "ADMIN CONTRACT SPECIALIST",
          "ADMIN TESTS & MEAS SPEC",
          "ADMIN TRAFFIC ENF AGNT-UNION",
          "ADMIN TRAFFIC ENFRCMNT AGENT",
          "ADMINISTRATIVE ACCOUNTANT",
          "ADMINISTRATIVE ARCHITECT",
          "ADMINISTRATIVE CITY PLANNER",
          "ADMINISTRATIVE COMMUNITY RELATIONS SPECIALIST",
          "ADMINISTRATIVE CONTRACT SPECIALIST",
          "ADMINISTRATIVE ENGINEER",
          "ADMINISTRATIVE GRAPHIC ARTIST",
          "ADMINISTRATIVE INVESTIGATOR",
          "ADMINISTRATIVE LABOR RELATIONS ANALYST",
          "ADMINISTRATIVE MANAGEMENT AUDITOR",
          "ADMINISTRATIVE MANAGER",
          "ADMINISTRATIVE PRINTING SERVICES MANAGER",
          "ADMINISTRATIVE PROCUREMENT ANALYST-NON-MGRL",
          "ADMINISTRATIVE PROJECT MANAGER",
          "ADMINISTRATIVE PSYCHOLOGIST",
          "ADMINISTRATIVE PUBLIC HEALTH SANITARIAN",
          "ADMINISTRATIVE PUBLIC INFORMATION SPECIALIST NM FORMER M1/M2",
          "ADMINISTRATIVE PUBLIC INFORMATION SPECIALIST",
          "ADMINISTRATIVE QUALITY ASSURANCE SPECIALIST",
          "ADMINISTRATIVE STAFF ANALYST",
          "ADMINISTRATIVE SUPERVISOR OF BUILDING MAINTENANCE",
          "ADMINISTRATIVE TRANSPORTATION COORDINATOR",
          "AGENCY ATTORNEY INTERNE",
          "AGENCY ATTORNEY",
          "AGENCY CHIEF CONTRACTING OFFICER",
          "ARCHITECT",
          "ASSISTANT ARCHITECT",
          "ASSISTANT COMMISSIONER",
          "ASSISTANT COUNSEL-PD",
          "ASSISTANT DEPUTY COMMISSIONER",
          "ASSISTANT MECHANICAL ENGINEER",
          "ASSISTANT PRINTING PRESS OPERATOR",
          "ASSISTANT SUPERVISING CHIEF SURGEON",
          "ASSISTANT TO POLICE COMMISSIONOR",
          "ASSOC SPVR OF SCHOOL SECURITY",
          "ASSOC SUPVR OF SCHL SEC",
          "ASSOCIATE FINGERPRINT TECHNICIAN",
          "ASSOCIATE INVESTIGATOR",
          "ASSOCIATE LABOR RELATIONS ANALYST",
          "ASSOCIATE PARKING CONTROL SPECIALIST",
          "ASSOCIATE PROJECT MANAGER",
          "ASSOCIATE PUBLIC RECORDS OFFICER",
          "ASSOCIATE STAFF ANALYST",
          "ASSOCIATE TRAFFIC ENFORCEMENT AGENT",
          "AUTO BODY WORKER",
          "AUTO MECHANIC",
          "AUTOMOTIVE SERVICE WORKER",
          "BOOKBINDER",
          "BOOKKEEPER",
          "CARPENTER",
          "CASE MANAGEMENT NURSE",
          "CASHIER",
          "CEMENT MASON",
          "CERTIFIED IT ADMINISTRATOR",
          "CERTIFIED IT DEVELOPER",
          "CHAPLAIN",
          "CHIEF OF STRATEGIC INITIATIVES",
          "CITY ATTENDANT",
          "CITY CUSTODIAL ASSISTANT",
          "CITY DENTIST",
          "CITY DEPUTY MEDICAL DIRECTOR",
          "CITY LABORER",
          "CITY RESEARCH SCIENTIST",
          "CIVILIANIZATION MANAGER-PD",
          "CLERICAL AIDE",
          "CLERICAL ASSOCIATE",
          "COLLEGE AIDE",
          "COMMISSIONER",
          "COMMUNITY ASSISTANT",
          "COMMUNITY ASSOCIATE",
          "COMMUNITY COORDINATOR",
          "COMPOSITOR",
          "COMPUTER ASSOCIATE",
          "COMPUTER OPERATIONS MANAGER",
          "COMPUTER PROGRAMMER ANALYST",
          "COMPUTER SPECIALIST",
          "COMPUTER SYSTEMS MANAGER",
          "CONSTRUCTION PROJECT MANAGER",
          "COUNSEL TO THE POLICE COMMISSIONER",
          "CRIME ANALYST",
          "CRIMINALIST ASSISTANT DIRECTOR OF LABORATORY",
          "CRIMINALIST DEPUTY DIRECTOR OF LABATORY",
          "CRIMINALIST DIRECTOR OF LABORATORY",
          "CRIMINALIST",
          "CUSTODIAN",
          "DEPUTY CHIEF SURGEON",
          "DIRECTOR EMPLOYEE MANAGEMENT DIVISION",
          "DIRECTOR MANAGEMENT INFORMATION SYSTEMS",
          "DIRECTOR OF COMMUNICATIONS",
          "DIRECTOR OF DEPARTMENT ADVOCATES OFFICE",
          "DIRECTOR OF INTERNAL AFFAIRS - PD",
          "DIRECTOR OF MOTOR TRANSPORT",
          "DIRECTOR OF ORGANIZED CRIME CONTROL-PD",
          "DIRECTOR OF PHOTOGRAPHIC SERVICES-PD",
          "DIRECTOR OF PSYCHOLOGICAL SERVICES",
          "DIRECTOR OF SUPPORT SERVICES-PD",
          "DIRECTOR OF TECHNOLOGY DEVELOPMENT-PD",
          "DIRECTOR OF TRAINING",
          "DIRECTOR",
          "ECONOMIST",
          "ELECTRICAL ENGINEER",
          "ELECTRICIAN",
          "ELECTRICIANS HELPER",
          "ELEVATOR MECHANIC",
          "EMPLOYEE ASSISTANCE PROGRAM SPECIALIST",
          "EVIDENCE AND PROPERTY CONTROL SPECIALIST",
          "EXECUTIVE AGENCY COUNSEL",
          "FINGERPRINT TECHNICIAN TRAINEE",
          "FIRST DEPUTY COMMISSIONER",
          "FITNESS INSTRUCTOR",
          "GLAZIER",
          "GRAPHIC ARTIST",
          "HEALTH SERVICES MANAGER NON MANAGERIAL LEVEL I",
          "HORSESHOER",
          "HOSTLER",
          "INTELLIGENCE RESEARCH MANAGER-PD",
          "INTELLIGENCE RESEARCH SPECIALIST-PD",
          "INVESTIGATOR TRAINEE",
          "INVESTIGATOR",
          "IT AUTOMATION AND MONITORING ENGINEER",
          "IT PROJECT SPECIALIST",
          "IT SECURITY SPECIALIST",
          "IT SERVICE MANAGEMENT SPECIALIST",
          "LOCKSMITH",
          "MAINTENANCE WORKER",
          "MANAGEMENT AUDITOR TRAINEE",
          "MANAGEMENT AUDITOR",
          "MANAGER OF RADIO REPAIR OPERATIONS",
          "MARINE MAINTENANCE MECHANIC",
          "MEDIA SERVICES TECHNICIAN",
          "MOTOR VEHICLE OPERATOR",
          "MOTOR VEHICLE SUPERVISOR",
          "OFFICE MACHINE AIDE",
          "OILER",
          "OPERATIONS COMMUNICATIONS SPECIALIST",
          "PAINTER",
          "PARALEGAL AIDE",
          "PARKING CONTROL SPECIALIST",
          "PHOTOGRAPHER",
          "PHYSICIAN'S ASSISTANT",
          "PLUMBER",
          "PLUMBER'S HELPER",
          "POLICE ADMINISTRATIVE AIDE",
          "POLICE ATTENDANT",
          "POLICE CADET",
          "POLICE COMMUNICATIONS TECHNICIAN",
          "POLICE SURGEON",
          "PRECINCT COMMUNITY RELATIONS AIDE",
          "PRECINCT RECEPTIONIST",
          "PRINCIPAL ADMINISTRATIVE ASSOCIATE -  NON SUPVR",
          "PRINCIPAL FINGERPRINT TECHNICIAN",
          "PRINCIPAL POLICE COMMUNICATION TECHNICIAN",
          "PRINTING PRESS OPERATOR",
          "PROCUREMENT ANALYST",
          "PROGRAM PRODUCER",
          "PROJECT MANAGER",
          "PROPERTY CLERK",
          "PSYCHOLOGIST",
          "PUBLIC HEALTH ASSISTANT",
          "PUBLIC RECORDS OFFICER",
          "QUALITY ASSURANCE SPECIALIST",
          "RADIO REPAIR MECHANIC",
          "RESEARCH ASSISTANT",
          "ROOFER",
          "SCHOOL CROSSING GUARD",
          "SCHOOL SAFETY AGENT",
          "SECRETARY OF THE DEPARTMENT",
          "SECRETARY TO THE COMMISSIONER",
          "SECRETARY TO THE FIRST DEPUTY COMMISSIONER-PD",
          "SECRETARY",
          "SENIOR IT ARCHITECT",
          "SENIOR OFFICE APPLIANCE MAINTAINER",
          "SENIOR PHOTOGRAPHER",
          "SENIOR POLICE ADMINISTRATIVE AIDE",
          "SENIOR STATIONARY ENGINEER",
          "SHEET METAL WORKER",
          "SPECIAL OFFICER",
          "STAFF ANALYST TRAINEE",
          "STAFF ANALYST",
          "STATIONARY ENGINEER",
          "STEAM FITTER",
          "STEAM FITTER'S HELPER",
          "STENOGRAPHER TO EACH DEPUTY COMMISSIONER",
          "STENOGRAPHIC SPECIALIST",
          "STOCK WORKER",
          "SUMMER COLLEGE INTERN",
          "SUMMER GRADUATE INTERN",
          "SUPERVISING CHIEF SURGEON",
          "SUPERVISING POLICE COMMUNICATIONS TECHNICIAN",
          "SUPERVISOR CARPENTER",
          "SUPERVISOR ELECTRICIAN",
          "SUPERVISOR ELEVATOR MECHANIC",
          "SUPERVISOR GLAZIER",
          "SUPERVISOR LOCKSMITH",
          "SUPERVISOR OF MECHANICAL INSTALLATIONS & MAINTENANCE",
          "SUPERVISOR OF MECHANICS",
          "SUPERVISOR OF OFFICE MACHINE OPERATIONS",
          "SUPERVISOR OF RADIO REPAIR OPERATIONS",
          "SUPERVISOR OF SCHOOL SECURITY",
          "SUPERVISOR OF STOCK WORKERS",
          "SUPERVISOR PAINTER",
          "SUPERVISOR PLUMBER",
          "SUPERVISOR ROOFER",
          "SUPERVISOR SHEET METAL WORKER",
          "SUPERVISOR STEAMFITTER",
          "SUPERVISOR THERMOSTAT REPAIR",
          "SUPERVISOR",
          "TELECOMMUNICATIONS ASSOCIATE",
          "TELEPHONE SERVICE TECHNICIAN",
          "TESTS AND MEASUREMENT SPECIALIST",
          "THERMOSTAT REPAIRER",
          "TRAFFIC ENFORCEMENT AGENT",
          "WELDER");

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

  /** Column headers for the payroll data, since they're not included in the data file. */
  private static final String[] PAYROLL_HEADERS = {
    "Fiscal Year",
    "Payroll Number",
    "Agency Name",
    "Last Name",
    "First Name",
    "Mid Init",
    "Agency Start Date",
    "Work Location Borough",
    "Title Description",
    "Leave Status as of June 30",
    "Base Salary",
    "Pay Basis",
    "Regular Hours",
    "Regular Gross Paid",
    "OT Hours",
    "Total OT Paid",
    "Total Other Pay"
  };

  @Option(name = "-profile", usage = "NYPD CSV profile data.")
  private File profileFile;

  @Option(name = "-payroll", usage = "NYC CSV payroll data for NYPD.")
  private File payrollFile;

  @Option(name = "-output-dir", usage = "Directory to output the merged data as CSV.")
  private File outputDir;

  public static void main(String[] args) throws CmdLineException, CsvException, IOException {
    new ProfilePayroll().doMain(args);
  }

  private void doMain(String[] args) throws CmdLineException, CsvException, IOException {
    CmdLineParser parser = new CmdLineParser(this);
    parser.parseArgument(args);

    List<Profile> profiles = readProfiles(profileFile);
    String[] profileHeaders = profiles.remove(0).getRaw();
    ImmutableList<Profile> allProfiles = ImmutableList.copyOf(profiles);

    SortedMap<String, ArrayListMultimap<String, Payroll>> payrolls = readPayroll(payrollFile);

    int totalProfiles = profiles.size();

    Map<String, List<Merged>> merged = new HashMap<>();
    Map<String, List<Profile>> leftoverProfiles = new HashMap<>();
    for (String year : payrolls.keySet()) {
      List<Profile> profilesCopy = new ArrayList<>(allProfiles);

      List<Merged> mergedYear = merge(profilesCopy, payrolls.get(year));
      merged.put(year, mergedYear);
      leftoverProfiles.put(year, profilesCopy);

      System.out.printf(
          "%s: merged %s out of %s profiles (%s unmerged profiles, %s unmerged payrolls)%n",
          year,
          mergedYear.size(),
          totalProfiles,
          totalProfiles - mergedYear.size(),
          payrolls.get(year).size());
    }

    output(merged, leftoverProfiles, payrolls, profileHeaders);
  }

  private List<Profile> readProfiles(File profileFile) throws CsvException, IOException {
    CSVReader reader = new CSVReader(new FileReader(profileFile));
    return reader.readAll().stream().map(Profile::new).collect(toCollection(ArrayList::new));
  }

  private SortedMap<String, ArrayListMultimap<String, Payroll>> readPayroll(File payrollFile)
      throws CsvException, IOException {
    CSVReader reader = new CSVReader(new FileReader(payrollFile));
    List<String[]> unfiltered = reader.readAll();
    SortedMap<String, ArrayListMultimap<String, Payroll>> years = new TreeMap<>();

    for (String[] row : unfiltered) {
      Payroll payroll = new Payroll(row);
      if (TITLES_TO_REMOVE.contains(payroll.getTitle())) {
        continue;
      }
      // Payroll data has a bunch of entries with no names which we can't do anything with.
      if (payroll.getFirstName().isEmpty() && payroll.getLastName().isEmpty()) {
        continue;
      }
      if (!years.containsKey(payroll.getYear())) {
        years.put(payroll.getYear(), ArrayListMultimap.create());
      }
      years.get(payroll.getYear()).put(payroll.getLastName(), payroll);
    }

    return years;
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
      Payroll match = findManualMatch(profile, payrolls);
      if (match != null) {
        return match;
      }
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

    // If there are multiple matches, choose the one that has the most regular pay.
    List<Payroll> matchingList = new ArrayList<>(matchingFirstNames);
    matchingList.sort((o1, o2) -> o1.getRegularPay().compareTo(o2.getRegularPay()));
    return matchingList.get(matchingList.size() - 1);
  }

  private Payroll findManualMatch(Profile profile, List<Payroll> payrolls) {
    checkState(MANUAL_MATCHES.containsKey(profile.getTaxId()), profile);
    for (Payroll payroll : payrolls) {
      if (MANUAL_MATCHES.get(profile.getTaxId()).equals(payroll.getBorough())) {
        return payroll;
      }
    }
    // Manual matches are set up for 2021 payroll data, so may not match with previous years.
    return null;
  }

  private static LocalDate parseDate(String date) {
    return LocalDate.parse(date, DATE_FORMAT);
  }

  private void output(
      Map<String, List<Merged>> merged,
      Map<String, List<Profile>> leftoverProfiles,
      SortedMap<String, ArrayListMultimap<String, Payroll>> leftoverPayrolls,
      String[] profileHeaders)
      throws IOException {
    if (!outputDir.exists()) {
      outputDir.mkdir();
    }

    for (String year : merged.keySet()) {
      File yearOutput = new File(outputDir, String.format("payroll_%s.csv", year));
      CSVWriter writer = new CSVWriter(new FileWriter(yearOutput));

      String[] allHeaders = ObjectArrays.concat(profileHeaders, PAYROLL_HEADERS, String.class);
      writer.writeNext(allHeaders);

      merged.get(year).stream().map(Merged::getRows).forEach(writer::writeNext);
      leftoverProfiles.get(year).stream().map(Profile::getRaw).forEach(writer::writeNext);

      String[] blankProfile = new String[profileHeaders.length];
      leftoverPayrolls.get(year).values().stream()
          .map(Payroll::getRaw)
          .map(p -> ObjectArrays.concat(blankProfile, p, String.class))
          .forEach(writer::writeNext);

      writer.close();
    }
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

    private BigDecimal getBaseSalary() {
      return new BigDecimal(rows[10]);
    }

    private BigDecimal getRegularPay() {
      return new BigDecimal(rows[13]);
    }

    private String getLeaveStatus() {
      return rows[9];
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
          .add("year", getYear())
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
