package emspishak.nypd.srgtraining;

import com.google.common.base.Joiner;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public final class SrgTraining {

  private static final String[] SRG_TRAINED_OUTPUT_HEADERS = {
    "last_name",
    "first_name",
    "badge_no",
    "rank",
    "command",
    "substantiated_count",
    "allegation_count",
    "srg_training_count",
    "duration_trained",
    "srg_trainings",
    "50a_link",
    "nypd_profile_link",
  };

  private static final ImmutableList<String> SRG_COMMANDS =
      ImmutableList.of(
          "STRATEGIC RESP GRP 1 MANHATTAN",
          "STRATEGIC RESP GRP 2 BRONX",
          "STRATEGIC RESP GRP 3 BROOKLYN",
          "STRATEGIC RESP GRP 4 QUEENS",
          "STRATEGIC RESP GRP 5 SI",
          "STRATEGIC RESPONSE GROUP");

  private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/u");

  @Option(name = "-profile-dir", usage = "Directory with NYPD profile JSON.")
  private File profileDir;

  @Option(name = "-50a-data", usage = "50-a server-cache.json file.")
  private File fiftyAData;

  @Option(name = "-output-dir", usage = "Directory for CSV outputs of SRG related officers.")
  private File outputDir;

  public static void main(String[] args) throws CmdLineException, IOException {
    new SrgTraining().doMain(args);
  }

  private void doMain(String[] args) throws CmdLineException, IOException {
    CmdLineParser parser = new CmdLineParser(this);
    parser.parseArgument(args);
    outputDir.mkdir();

    ImmutableMap<Integer, JSONObject> taxIds = get50AData();

    CSVWriter srgTrainedWriter =
        new CSVWriter(new FileWriter(new File(outputDir, "srg-trained.csv")));
    CSVWriter srgWriter = new CSVWriter(new FileWriter(new File(outputDir, "srg.csv")));
    srgTrainedWriter.writeNext(SRG_TRAINED_OUTPUT_HEADERS);
    srgWriter.writeNext(SRG_TRAINED_OUTPUT_HEADERS);

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

        ImmutableList<Training> trainings = getSrgTrainingDate(training);
        JSONObject matchedData = taxIds.get(taxId);

        if (!trainings.isEmpty()) {
          writeTrainingRow(srgTrainedWriter, profile, matchedData, trainings);
        }
        if (SRG_COMMANDS.contains(profile.getString("command"))) {
          writeTrainingRow(srgWriter, profile, matchedData, trainings);
        }
      }
    }

    srgTrainedWriter.close();
    srgWriter.close();
  }

  private static ImmutableList<Training> getSrgTrainingDate(JSONArray training) {
    ImmutableList.Builder<Training> trainings = ImmutableList.builder();
    for (int j = 0; j < training.length(); j++) {
      JSONObject course = training.getJSONObject(j);
      if (course.getString("name").startsWith("SRG")) {
        trainings.add(
            new Training(
                course.getString("name"),
                LocalDate.parse(course.getString("date"), INPUT_DATE_FORMAT)));
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

  private String getTrainingDuration(ImmutableList<Training> trainings) {
    if (trainings.size() < 2) {
      return "";
    }

    Training first = trainings.stream().min(Comparator.naturalOrder()).get();
    Training last = trainings.stream().max(Comparator.naturalOrder()).get();

    return Long.toString(first.date.until(last.date, ChronoUnit.DAYS));
  }

  private void writeTrainingRow(
      CSVWriter trainingWriter,
      JSONObject profile,
      JSONObject matched50AData,
      ImmutableList<Training> srgTrainings) {
    String[] row = {
      profile.getString("last_name"),
      profile.getString("first_name"),
      profile.getString("shield_no"),
      profile.getString("rank"),
      profile.getString("command"),
      matched50AData == null ? "0" : Integer.toString(matched50AData.getInt("substantiated_count")),
      matched50AData == null ? "0" : Integer.toString(matched50AData.getInt("allegation_count")),
      Integer.toString(srgTrainings.size()),
      getTrainingDuration(srgTrainings),
      Joiner.on('\n').join(srgTrainings),
      matched50AData == null
          ? ""
          : String.format(
              "https://www.50-a.org/officer/%s", matched50AData.getString("unique_mos")),
      String.format("https://oip.nypdonline.org/view/1/@TAXID=%s", profile.getInt("taxid"))
    };
    trainingWriter.writeNext(row);
  }

  private static final class Training implements Comparable<Training> {

    private final String name;
    private final LocalDate date;

    private Training(String name, LocalDate date) {
      this.name = name;
      this.date = date;
    }

    @Override
    public int compareTo(Training that) {
      return ComparisonChain.start()
          .compare(this.date, that.date)
          .compare(this.name, that.name)
          .result();
    }

    @Override
    public String toString() {
      return String.format("%s / %s", DateTimeFormatter.ISO_LOCAL_DATE.format(date), name);
    }
  }
}
