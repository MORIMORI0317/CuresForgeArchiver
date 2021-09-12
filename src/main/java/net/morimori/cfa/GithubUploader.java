package net.morimori.cfa;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.morimori.cfa.util.IkisugiUtils;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class GithubUploader {
    private static final Gson GSON = new Gson();
    private static GitHub github;
    private static GHRepository repository;

    public static void init(String token) throws IOException {
        github = new GitHubBuilder().withOAuthToken(token).build();
        repository = github.getRepository("MORIMORI0317/CuresForgeArchiver");
    }

    public static void updateTrace(JsonObject jsonObject) throws Exception {
        byte[] datas = IkisugiUtils.jsonBuilder(GSON.toJson(jsonObject)).getBytes(StandardCharsets.UTF_8);
        String sha = repository.getTree("main").getEntry("data").asTree().getEntry("trace.json").getSha();
        repository.createContent().content(datas).branch("main").path("data/trace.json").message("Update Trace " + new Date()).sha(sha).commit();
    }
}
