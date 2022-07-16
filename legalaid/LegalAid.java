package emspishak.nypd.legalaid;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.collect.ImmutableBiMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public final class LegalAid {

  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

  private static final Pattern CCRB_ID = Pattern.compile("(\\d{9})");

  @Option(name = "-resources", usage = "JSON file containing existing Closing Report links.")
  private File resources;

  public static void main(String[] args) throws CmdLineException, IOException {
    new LegalAid().doMain(args);
  }

  private void doMain(String[] args) throws CmdLineException, IOException {
    CmdLineParser parser = new CmdLineParser(this);
    parser.parseArgument(args);

    Mode mode = LinkMode.create(resources);

    String url =
        "https://api.www.documentcloud.org/api/documents/search/?organization=2723&q=%20%22ccrb%20investigative%20recommendation%22%20%22case%20summary%22&version=2.0&format=json";
    while (url != null) {
      JSONObject json = fetchJson(url);
      JSONArray docs = json.getJSONArray("results");
      for (int i = 0; i < docs.length(); i++) {
        JSONObject doc = docs.getJSONObject(i);
        mode.process(doc);
      }

      if (json.isNull("next")) {
        url = null;
      } else {
        url = json.getString("next");
      }
    }

    mode.finish();
  }

  private static JSONObject fetchJson(String url) throws IOException {
    GenericUrl gurl = new GenericUrl(url);
    HttpRequest request = HTTP_TRANSPORT.createRequestFactory().buildGetRequest(gurl);
    HttpResponse response = request.execute();

    return new JSONObject(response.parseAsString());
  }

  private static interface Mode {
    void process(JSONObject responseJson);

    void finish();
  }

  private static final class LinkMode implements Mode {

    private final Map<Integer, String> urls;
    private final Scanner in;
    private final ImmutableBiMap<Integer, String> idToUrl;
    private final ImmutableBiMap<String, Integer> urlToId;

    private LinkMode(ImmutableBiMap<Integer, String> idToUrl) {
      urls = new LinkedHashMap<>();
      in = new Scanner(System.in);
      this.idToUrl = idToUrl;
      urlToId = idToUrl.inverse();
    }

    private static LinkMode create(File resources) throws IOException {
      JSONArray complaints =
          new JSONObject(Files.readString(resources.toPath())).getJSONArray("complaints");
      ImmutableBiMap.Builder<Integer, String> idToUrl = ImmutableBiMap.builder();

      for (int i = 0; i < complaints.length(); i++) {
        JSONObject complaint = complaints.getJSONObject(i);
        if ("Complaint Closing Report".equals(complaint.getString("title"))) {
          idToUrl.put(
              Integer.parseInt(complaint.getString("complaint")), complaint.getString("url"));
        }
      }

      return new LinkMode(idToUrl.build());
    }

    @Override
    public void process(JSONObject doc) {
      String docUrl = doc.getString("canonical_url");
      if (urlToId.containsKey(docUrl)) {
        System.out.println("already have " + docUrl);
        return;
      }

      Matcher m = CCRB_ID.matcher(doc.getString("title"));
      int id;
      if (m.find()) {
        id = Integer.parseInt(m.group(1));
      } else {
        System.out.printf("Enter ID for %s : ", docUrl);
        id = in.nextInt();
        if (id == 0) {
          return;
        }
      }

      if (idToUrl.containsKey(id)) {
        System.out.println("already have " + id);
        return;
      }

      if (urls.containsKey(id)) {
        System.out.printf(
            "duplicate for %s: %s and %s, enter URL to use: ", id, urls.get(id), docUrl);
        docUrl = in.nextLine();
      }
      urls.put(id, docUrl);
    }

    @Override
    public void finish() {
      for (Map.Entry<Integer, String> doc : urls.entrySet()) {
        System.out.println("    {");
        System.out.printf("      url: '%s',%n", doc.getValue());
        System.out.println("      title: 'Complaint Closing Report',");
        System.out.printf("      complaint: '%s'%n", doc.getKey());
        System.out.println("    },");
      }
    }
  }
}
