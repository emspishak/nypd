package emspishak.nypd.nsttraining;

import com.google.common.collect.ImmutableMap;
import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public final class NstTraining {

  private static final String NST_COURSE_NAME = "SRG - RAPID MOBILIZATION";

  private static final String[] OUTPUT_HEADERS = {
    "last_name",
    "first_name",
    "badge_no",
    "rank",
    "command",
    "substantiated_count",
    "allegation_count",
    "srg_trainings",
    "50a_link",
    "nypd_profile_link",
  };

  private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/u");

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

    ImmutableMap<Integer, JSONObject> taxIds = get50AData();

    CSVWriter writer = new CSVWriter(new FileWriter(outputFile));
    writer.writeNext(OUTPUT_HEADERS);

    for (char c = 'A'; c <= 'Z'; c++) {
      File jsonFile = new File(profileDir, String.format("nypd-profiles-%s.json", c));
      String contents = Files.readString(jsonFile.toPath());
      JSONArray json = new JSONArray(contents);

      for (int i = 0; i < json.length(); i++) {
        JSONObject profile = json.getJSONObject(i);
        JSONArray training;
        try {
          training = profile.getJSONObject("reports").getJSONArray("training");
        } catch (Exception e) {
          System.out.println("no training data found for " + profile.getString("full_name"));
          continue;
        }
        int taxId = profile.getInt("taxid");

        ImmutableList<String> trainings = getNstTrainingDate(training);
        if (!trainings.isEmpty()) {
          String[] row;
          JSONObject matchedData = taxIds.get(taxId);
          row =
              new String[] {
                profile.getString("last_name"),
                profile.getString("first_name"),
                profile.getString("shield_no"),
                profile.getString("rank"),
                profile.getString("command"),
                matchedData == null
                    ? "0"
                    : Integer.toString(matchedData.getInt("substantiated_count")),
                matchedData == null
                    ? "0"
                    : Integer.toString(matchedData.getInt("allegation_count")),
                Joiner.on('\n').join(trainings),
                matchedData == null
                    ? ""
                    : String.format(
                        "https://www.50-a.org/officer/%s", matchedData.getString("unique_mos")),
                String.format("https://oip.nypdonline.org/view/1/@TAXID=%s", taxId)
              };
          writer.writeNext(row);
        }
      }
    }

    writer.close();
  }

  private static ImmutableList<String> getNstTrainingDate(JSONArray training) {
    ImmutableList.Builder<String> trainings = ImmutableList.builder();
    for (int j = 0; j < training.length(); j++) {
      JSONObject course = training.getJSONObject(j);
      if (course.getString("name").startsWith("SRG")) {
        trainings.add(String.format("%s / %s", course.getString("date"), course.getString("name")));
      }
    }
    return trainings.build();
  }

  /** Maps from tax ID to 50a data blob. */
  private ImmutableMap<Integer, JSONObject> get50AData() throws IOException {
    String contents = Files.readString(fiftyAData.toPath());
    JSONObject json = new JSONObject(contents).getJSONObject("officers");

    ImmutableMap.Builder<Integer, JSONObject> data = ImmutableMap.builder();
    for (String mos : json.keySet()) {
      JSONObject profile = json.getJSONObject(mos);
      int taxId = profile.optInt("taxid");
      if (taxId > 0) {
        data.put(taxId, profile);
      }
    }

    return data.build();
  }
}
