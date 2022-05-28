package emspishak.nypd.legalaid;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public final class LegalAid {

  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

  // From
  // https://api.www.documentcloud.org/api/organizations/?individual=unknown&slug=&uuid=&id__in=&name=&name__istartswith=The+Legal+Aid+Society
  private static final int LEGAL_AID_ORGANIZATION_ID = 2723;

  @Option(name = "-output-dir", usage = "Directory to output files.")
  private File outputDir;

  public static void main(String[] args) throws CmdLineException, IOException {
    new LegalAid().doMain(args);
  }

  private void doMain(String[] args) throws CmdLineException, IOException {
    CmdLineParser parser = new CmdLineParser(this);
    parser.parseArgument(args);
    outputDir.mkdir();

    String url =
        String.format(
            "https://api.www.documentcloud.org/api/documents/?format=json&organization=%s&version=2.0",
            LEGAL_AID_ORGANIZATION_ID);
    while (url != null) {
      JSONObject json = fetchJson(url);
      JSONArray docs = json.getJSONArray("results");
      for (int i = 0; i < docs.length(); i++) {
        JSONObject doc = docs.getJSONObject(i);
        downloadDoc(doc);
      }

      if (json.isNull("next")) {
        url = null;
      } else {
        url = json.getString("next");
      }
    }
  }

  private static JSONObject fetchJson(String url) throws IOException {
    GenericUrl gurl = new GenericUrl(url);
    HttpRequest request = HTTP_TRANSPORT.createRequestFactory().buildGetRequest(gurl);
    HttpResponse response = request.execute();

    return new JSONObject(response.parseAsString());
  }

  private static String getDocUrl(JSONObject doc) {
    return String.format(
        "%sdocuments/%s/%s.pdf",
        doc.getString("asset_url"), doc.getInt("id"), doc.getString("slug"));
  }

  private static String getFilename(JSONObject doc) {
    return String.format("%s-%s.pdf", doc.getInt("id"), doc.getString("slug"));
  }

  private void downloadDoc(JSONObject doc) throws IOException {
    GenericUrl gurl = new GenericUrl(getDocUrl(doc));
    HttpRequest request = HTTP_TRANSPORT.createRequestFactory().buildGetRequest(gurl);
    try (OutputStream os = new FileOutputStream(new File(outputDir, getFilename(doc)))) {
      HttpResponse response = request.execute();
      response.download(os);
    }
  }
}
