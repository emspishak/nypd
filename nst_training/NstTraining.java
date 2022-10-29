package emspishak.nypd.nsttraining;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public final class NstTraining {

  private static final ImmutableSet<String> NST_COURSE_NAMES = ImmutableSet.of(
    "NEIGHBORHOOD SAFETY TEAM TRAINING, 7-DAY COURSE",
    "DASHBOARD CAMERA FOR NEIGHBORHOOD SAFETY TEAMS");

  private static final String[] OUTPUT_HEADERS = {
    "last_name",
    "first_name",
    "tax_id",
    "badge_no",
    "rank",
    "command",
    "substantiated_count",
    "allegation_count",
    "50a_link",
    "nypd_profile_link",
    "lawsuits_count",
    "complaint_docs",
    "officer_docs"
  };

  private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/u");

  private static final Joiner NEW_LINE = Joiner.on('\n');

  @Option(name = "-profile-dir", usage = "Directory with NYPD profile JSON.")
  private File profileDir;

  @Option(name = "-50a-data", usage = "50-a server-cache.json file.")
  private File fiftyAData;

  @Option(name = "-output", usage = "File for CSV output of officers with NST training.")
  private File outputFile;

  public static void main(String[] args) throws CmdLineException, IOException {
    new NstTraining().doMain(args);
  }

  private void doMain(String[] args) throws CmdLineException, IOException {
    CmdLineParser parser = new CmdLineParser(this);
    parser.parseArgument(args);

    JSONObject json = new JSONObject(Files.readString(fiftyAData.toPath()));
    ImmutableMap<Integer, JSONObject> taxIds = get50AData(json);
    ImmutableMap<String, JSONObject> complaints = get50AComplaints(json);

    CSVWriter writer = new CSVWriter(new FileWriter(outputFile));
    writer.writeNext(OUTPUT_HEADERS);

    for (char c = 'A'; c <= 'Z'; c++) {
      File jsonFile = new File(profileDir, String.format("nypd-profiles-%s.json", c));
      String contents = Files.readString(jsonFile.toPath());
      JSONArray profiles = new JSONArray(contents);

      for (int i = 0; i < profiles.length(); i++) {
        JSONObject profile = profiles.getJSONObject(i);
        JSONArray training;
        try {
          training = profile.getJSONObject("reports").getJSONArray("training");
        } catch (Exception e) {
          System.out.println("no training data found for " + profile.getString("full_name"));
          continue;
        }
        int taxId = profile.getInt("taxid");

        if (isNst(training)) {
          String[] row;
          JSONObject matchedData = taxIds.get(taxId);
          row =
              new String[] {
                profile.getString("last_name"),
                profile.getString("first_name"),
                Integer.toString(taxId),
                profile.getString("shield_no"),
                profile.getString("rank"),
                profile.getString("command"),
                matchedData == null
                    ? "0"
                    : Integer.toString(matchedData.getInt("substantiated_count")),
                matchedData == null
                    ? "0"
                    : Integer.toString(matchedData.getInt("allegation_count")),
                matchedData == null
                    ? ""
                    : String.format(
                        "https://www.50-a.org/officer/%s", matchedData.getString("unique_mos")),
                String.format("https://oip.nypdonline.org/view/1/@TAXID=%s", taxId),
                getLawsuitsCount(matchedData),
                NEW_LINE.join(getComplaintDocuments(matchedData, complaints)),
                NEW_LINE.join(getOfficerDocuments(matchedData))
              };
          writer.writeNext(row);
        }
      }
    }

    writer.close();
  }

  private static boolean isNst(JSONArray training) {
    Set<String> courseNames = new HashSet<>();
    for (int j = 0; j < training.length(); j++) {
      JSONObject course = training.getJSONObject(j);
      courseNames.add(course.getString("name"));
    }
    return courseNames.containsAll(NST_COURSE_NAMES);
  }

  /** Maps from tax ID to 50a data blob. */
  private ImmutableMap<Integer, JSONObject> get50AData(JSONObject json) throws IOException {
    JSONObject officers = json.getJSONObject("officers");

    ImmutableMap.Builder<Integer, JSONObject> data = ImmutableMap.builder();
    for (String mos : officers.keySet()) {
      JSONObject profile = officers.getJSONObject(mos);
      int taxId = profile.optInt("taxid");
      if (taxId > 0) {
        data.put(taxId, profile);
      }
    }

    return data.build();
  }

  private ImmutableMap<String, JSONObject> get50AComplaints(JSONObject json) throws IOException {
    JSONObject complaints = json.getJSONObject("complaints");

    ImmutableMap.Builder<String, JSONObject> data = ImmutableMap.builder();
    for (String id : complaints.keySet()) {
      JSONObject complaint = complaints.getJSONObject(id);
      data.put(id, complaint);
    }

    return data.build();
  }

  private ImmutableList<String> getComplaintDocuments(
      JSONObject officer, ImmutableMap<String, JSONObject> allComplaints) {
    if (officer == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<String> docs = ImmutableList.builder();

    for (Object id : officer.getJSONArray("complaints")) {
      JSONObject complaint = allComplaints.get((String) id);
      if (complaint.has("resources")) {
        JSONArray complaints = complaint.getJSONObject("resources").getJSONArray("complaints");
        for (Object detailsObj : complaints) {
          JSONObject details = (JSONObject) detailsObj;
          String url = details.getString("url");
          if (url.startsWith("/")) {
            url = String.format("https://50-a.org%s", url);
          }
          docs.add(url);
        }
      }
    }

    return docs.build();
  }

  private String getLawsuitsCount(JSONObject officer) {
    if (officer == null) {
      return "";
    }

    if (officer.has("lawsuits")) {
      return Integer.toString(officer.getJSONObject("lawsuits").getJSONArray("cases").length());
    }
    return "";
  }

  private ImmutableList<String> getOfficerDocuments(JSONObject officer) {
    if (officer == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<String> docs = ImmutableList.builder();
    if (officer.has("documents")) {
      JSONArray jsonDocuments = officer.getJSONArray("documents");
      for (int i = 0; i < jsonDocuments.length(); i++) {
        docs.add(jsonDocuments.getJSONObject(i).getString("url"));
      }
    }
    return docs.build();
  }
}
