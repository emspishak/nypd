package emspishak.nypd.srgtraining;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Joiner;
import com.google.common.collect.Comparators;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ObjectArrays;
import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public final class SrgTraining {

  private static final String[] COMMON_OUTPUT_HEADERS = {
    "last_name",
    "first_name",
    "badge_no",
    "rank",
    "command",
    "assignment_date",
    "substantiated_count",
    "allegation_count",
    "50a_link",
    "nypd_profile_link",
  };

  private static final String[] SRG_TRAINED_OUTPUT_HEADERS =
      ObjectArrays.concat(
          COMMON_OUTPUT_HEADERS,
          new String[] {
            "srg_training_count", "srg_trainings",
          },
          String.class);

  private static final String[] SRG_TRAINING_OUTPUT_HEADERS =
      ObjectArrays.concat(
          COMMON_OUTPUT_HEADERS, new String[] {"training", "training_date"}, String.class);

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
    srgTrainedWriter.writeNext(SRG_TRAINED_OUTPUT_HEADERS);

    CSVWriter srgWriter = new CSVWriter(new FileWriter(new File(outputDir, "srg.csv")));
    srgWriter.writeNext(SRG_TRAINED_OUTPUT_HEADERS);

    CSVWriter srgTrainingsWriter =
        new CSVWriter(new FileWriter(new File(outputDir, "srg-trainings.csv")));
    srgTrainingsWriter.writeNext(SRG_TRAINING_OUTPUT_HEADERS);

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
        LocalDate assignmentDate = getAssignmentDate(profile);

        ImmutableList<Training> allTrainings = getTrainings(training);
        ImmutableList<Training> srgTrainings =
            filterTrainings(allTrainings, t -> t.name.startsWith("SRG"));
        JSONObject matchedData = taxIds.get(taxId);

        if (!srgTrainings.isEmpty()) {
          writeOfficerRow(srgTrainedWriter, profile, matchedData, srgTrainings, assignmentDate);
        }
        if (SRG_COMMANDS.contains(profile.getString("command"))) {
          writeOfficerRow(srgWriter, profile, matchedData, srgTrainings, assignmentDate);
          ImmutableList<Training> trainingsAfterAsignment =
              filterTrainings(
                  allTrainings,
                  t -> !t.date.isPresent() || t.date.get().compareTo(assignmentDate) >= 0);
          for (Training t : trainingsAfterAsignment) {
            writeTrainingRow(srgTrainingsWriter, profile, matchedData, t, assignmentDate);
          }
        }
      }
    }

    srgTrainedWriter.close();
    srgWriter.close();
    srgTrainingsWriter.close();
  }

  private static ImmutableList<Training> getTrainings(JSONArray training) {
    ImmutableList.Builder<Training> trainings = ImmutableList.builder();
    for (int j = 0; j < training.length(); j++) {
      JSONObject course = training.getJSONObject(j);
      if (course.has("date")) {
        trainings.add(
            new Training(
                course.getString("name"),
                LocalDate.parse(course.getString("date"), INPUT_DATE_FORMAT)));
      } else {
        trainings.add(new Training(course.getString("name")));
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

  private LocalDate getAssignmentDate(JSONObject profile) {
    return LocalDate.parse(
        profile.getJSONObject("reports").getJSONObject("summary").getString("assignment_date"),
        INPUT_DATE_FORMAT);
  }

  private ImmutableList<Training> filterTrainings(
      ImmutableList<Training> trainings, Predicate<Training> predicate) {
    return trainings.stream().filter(predicate).collect(toImmutableList());
  }

  private String[] getRowCommon(
      JSONObject profile, JSONObject matched50AData, LocalDate assignmentDate) {
    return new String[] {
      profile.getString("last_name"),
      profile.getString("first_name"),
      profile.getString("shield_no"),
      profile.getString("rank"),
      profile.getString("command"),
      DateTimeFormatter.ISO_LOCAL_DATE.format(assignmentDate),
      matched50AData == null ? "0" : Integer.toString(matched50AData.getInt("substantiated_count")),
      matched50AData == null ? "0" : Integer.toString(matched50AData.getInt("allegation_count")),
      matched50AData == null
          ? ""
          : String.format(
              "https://www.50-a.org/officer/%s", matched50AData.getString("unique_mos")),
      String.format("https://oip.nypdonline.org/view/1/@TAXID=%s", profile.getInt("taxid")),
    };
  }

  private void writeOfficerRow(
      CSVWriter officerWriter,
      JSONObject profile,
      JSONObject matched50AData,
      ImmutableList<Training> srgTrainings,
      LocalDate assignmentDate) {
    String[] row =
        ObjectArrays.concat(
            getRowCommon(profile, matched50AData, assignmentDate),
            new String[] {
              Integer.toString(srgTrainings.size()), Joiner.on('\n').join(srgTrainings),
            },
            String.class);
    officerWriter.writeNext(row);
  }

  private void writeTrainingRow(
      CSVWriter trainingWriter,
      JSONObject profile,
      JSONObject matched50AData,
      Training training,
      LocalDate assignmentDate) {
    String[] row =
        ObjectArrays.concat(
            getRowCommon(profile, matched50AData, assignmentDate),
            new String[] {
              training.name, training.date.map(DateTimeFormatter.ISO_LOCAL_DATE::format).orElse(""),
            },
            String.class);
    trainingWriter.writeNext(row);
  }

  private static final class Training implements Comparable<Training> {

    private final String name;
    private final Optional<LocalDate> date;

    private Training(String name, LocalDate date) {
      this.name = name;
      this.date = Optional.of(date);
    }

    private Training(String name) {
      this.name = name;
      this.date = Optional.empty();
    }

    @Override
    public int compareTo(Training that) {
      return ComparisonChain.start()
          .compare(this.date, that.date, Comparators.emptiesLast(Comparator.naturalOrder()))
          .compare(this.name, that.name)
          .result();
    }

    @Override
    public String toString() {
      return String.format(
          "%s / %s", date.map(DateTimeFormatter.ISO_LOCAL_DATE::format).orElse(""), name);
    }
  }
}
