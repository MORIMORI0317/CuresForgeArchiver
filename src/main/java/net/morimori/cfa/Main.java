package net.morimori.cfa;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.therandomlabs.curseapi.CurseAPI;
import com.therandomlabs.curseapi.CurseException;
import com.therandomlabs.curseapi.file.CurseFile;
import com.therandomlabs.curseapi.file.CurseFiles;
import com.therandomlabs.curseapi.game.CurseCategorySection;
import com.therandomlabs.curseapi.game.CurseGame;
import com.therandomlabs.curseapi.minecraft.CurseAPIMinecraft;
import com.therandomlabs.curseapi.project.CurseProject;
import com.therandomlabs.curseapi.project.CurseSearchQuery;
import com.therandomlabs.curseapi.project.CurseSearchSort;
import net.morimori.cfa.util.IkisugiUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class Main {
    private static final Gson GSON = new Gson();
    public static Map<Integer, Set<Integer>> lastTraces = new HashMap<>();

    public static void main(String[] args) throws Exception {
        System.out.println("Start Program");

        JsonObject config = GSON.fromJson(new FileReader("config.json"), JsonObject.class);
        CurseAPIMinecraft.initialize();
        GithubUploader.init(config.get("GitToken").getAsString());
        //  archiveStart();
        IkisugiUtils.clockTimer(12, 0, () -> {
            try {
                archiveStart();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }

    public static void archiveStart() throws Exception {
        System.out.println(new Date());
        System.out.println("Start");

        Map<Integer, Set<Integer>> gitTraces = new HashMap<>();

        try {
            JsonObject jo = IkisugiUtils.getURLJsonResponse("https://raw.githubusercontent.com/MORIMORI0317/CuresForgeArchiver/main/data/trace.json");
            for (Map.Entry<String, JsonElement> stringJsonElementEntry : jo.entrySet()) {
                Set<Integer> st = new HashSet<>();
                for (JsonElement jsonElement : stringJsonElementEntry.getValue().getAsJsonArray()) {
                    st.add(jsonElement.getAsInt());
                }
                gitTraces.put(Integer.parseInt(stringJsonElementEntry.getKey()), st);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (gitTraces.isEmpty())
            gitTraces = lastTraces;


        CurseGame game = CurseAPI.game(CurseAPIMinecraft.MINECRAFT_ID).get();
        Set<CurseCategorySection> sections = game.categorySections();
        Map<Integer, List<CurseProject>> prsrank = new HashMap<>();
        Map<Integer, Set<Integer>> prs = new HashMap<>();
        Set<Integer> ids = new HashSet<>();
        for (CurseCategorySection section : sections) {

            ids.add(section.id());
            Thread chk = new Thread(() -> {
                long stTime = System.currentTimeMillis();
                try {
                    List<CurseProject> ors = get10000PopularityMods(game, section.id());
                    if (System.currentTimeMillis() - stTime > 1000 * 60 * 10)
                        return;
                    prs.put(section.id(), new HashSet<>());
                    prs.get(section.id()).addAll(ors.stream().map(CurseProject::id).toList());
                    prsrank.put(section.id(), new ArrayList<>());
                    prsrank.get(section.id()).addAll(ors);
                } catch (CurseException e) {
                    e.printStackTrace();
                    if (System.currentTimeMillis() - stTime > 1000 * 60 * 10)
                        return;
                    prs.put(section.id(), new HashSet<>());
                    prsrank.put(section.id(), new ArrayList<>());
                } finally {
                    if (!(System.currentTimeMillis() - stTime > 1000 * 60 * 10)) {
                        ids.remove(section.id());
                    }
                }
            });
            chk.start();
        }
        long stTime = System.currentTimeMillis();
        do {
            Thread.sleep(100);
            if (System.currentTimeMillis() - stTime > 1000 * 60 * 10)
                break;
        } while (!ids.isEmpty());

        if (!ids.isEmpty())
            System.out.println("Time Out: " + ids);

        for (Map.Entry<Integer, Set<Integer>> integerListEntry : prs.entrySet()) {
            if (!gitTraces.containsKey(integerListEntry.getKey()))
                gitTraces.put(integerListEntry.getKey(), new HashSet<>());
            gitTraces.get(integerListEntry.getKey()).addAll(integerListEntry.getValue());
        }
        addMyAuthorData(game, gitTraces);

        lastTraces = gitTraces;
        JsonObject updateTrace = new JsonObject();
        for (Map.Entry<Integer, Set<Integer>> integerListEntry : gitTraces.entrySet()) {
            JsonArray array = new JsonArray();
            for (int curseProject : integerListEntry.getValue()) {
                array.add(curseProject);
            }
            updateTrace.add(String.valueOf(integerListEntry.getKey()), array);
        }
        GithubUploader.updateTrace(updateTrace);

        for (CurseCategorySection section : sections) {
            Map<Integer, Set<Integer>> finalGitTraces = gitTraces;
            Thread chk = new Thread(() -> {
                try {
                    archiveSection(section, finalGitTraces.get(section.id()), prsrank.get(section.id()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            chk.start();
        }
    }

    public static void archiveSection(CurseCategorySection section, Set<Integer> traces, List<CurseProject> rank) throws Exception {
        System.out.println("Start Data Collect: " + section.name());
        File sfile = new File("archive_" + section.name());
        sfile.mkdirs();
        SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd");
        Date date = new Date();
        String nowdate = sf.format(date);

        JsonObject jo = new JsonObject();
        jo.add("date", dateJo(ZonedDateTime.now()));
        jo.addProperty("time", System.currentTimeMillis());
        JsonArray ja = new JsonArray();
        int pcont = 0;
        for (int trace : traces) {
            try {
                CurseProject project = null;
                project = rank.stream().filter(n -> n.id() == trace).findFirst().orElse(null);
                if (project == null)
                    project = CurseAPI.project(trace).orElse(null);

                if (project == null)
                    continue;

                JsonObject jao = new JsonObject();
                if (rank.stream().anyMatch(n -> n.id() == trace)) {
                    int ranking = rank.indexOf(project);
                    jao.addProperty("rank", ranking);
                }
                jao.addProperty("name", project.name());
                jao.addProperty("summary", project.summary());
                jao.addProperty("slug", project.slug());
                jao.addProperty("id", project.id());
                JsonArray jaa = new JsonArray();
                project.authors().stream().forEach(n -> jaa.add(n.name()));
                jao.add("authors", jaa);
                jao.addProperty("url", project.url().query());
                jao.addProperty("downloadcount", project.downloadCount());
                jao.add("creationtime", dateJo(project.creationTime()));
                jao.add("lastupdatetime", dateJo(project.lastUpdateTime()));
                jao.add("lastmodificationtime", dateJo(project.lastModificationTime()));
                CurseFiles<CurseFile> files = project.files();
                Map<String, List<CurseFile>> vf = new HashMap<>();
                for (CurseFile file : files) {
                    file.gameVersions().forEach(n -> {
                        if (!vf.containsKey(n.versionString()))
                            vf.put(n.versionString(), new ArrayList<>());

                        vf.get(n.versionString()).add(file);
                    });
                }
                JsonArray javf = new JsonArray();
                vf.forEach((n, m) -> {
                    JsonObject javo = new JsonObject();
                    javo.addProperty("version", n);
                    javo.addProperty("cont", m.size());
                    long lastTime = 0;
                    ZonedDateTime zdt = null;
                    long mostSize = 0;
                    for (CurseFile curseFile : m) {
                        Instant instant = curseFile.uploadTime().toInstant();
                        Date stdate = Date.from(instant);

                        long time = stdate.getTime();
                        if (lastTime < time) {
                            lastTime = time;
                            zdt = curseFile.uploadTime();
                        }
                        long size = curseFile.fileSize();

                        if (mostSize < size)
                            mostSize = size;

                    }
                    if (zdt != null)
                        javo.add("lastupdate", dateJo(zdt));
                    javo.addProperty("mostSize", mostSize);
                    javf.add(javo);
                });
                jao.add("versionfiles", javf);
                JsonArray catja = new JsonArray();
                project.categories().forEach(n -> {
                    JsonObject catjo = new JsonObject();
                    catjo.addProperty("name", n.name());
                    catjo.addProperty("id", n.id());
                    catja.add(catjo);
                });
                jao.add("categories", catja);

                ja.add(jao);
                pcont++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        jo.add("projects", ja);
        jo.add("finishdate", dateJo(ZonedDateTime.now()));

        byte[] data = zipGz(new ByteArrayInputStream(GSON.toJson(jo).getBytes(StandardCharsets.UTF_8))).readAllBytes();
        Files.write(Paths.get("archive_" + section.name()).resolve(nowdate + ".gz").toFile().toPath(),data);
    }

    public static List<CurseProject> get10000PopularityMods(CurseGame game, int sectionID) throws CurseException {
        int traycont = 0;
        while (traycont <= 10) {
            try {
                List<CurseProject> ps = new ArrayList<>();
                for (int i = 0; i < 250; i++) {
                    int cont = 40;
                    CurseSearchQuery csq = new CurseSearchQuery().game(game).sortingMethod(CurseSearchSort.POPULARITY).pageSize(cont).pageIndex(i * cont).categorySectionID(sectionID);
                    List<CurseProject> projects = CurseAPI.searchProjects(csq).get();
                    ps.addAll(projects);
                }
                return ps;
            } catch (Exception ex) {
                ex.printStackTrace();
                traycont++;
            }
        }
        return null;
    }

    public static InputStream zipGz(InputStream data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(baos);
        gos.write(data.readAllBytes());
        gos.close();
        baos.close();
        return new ByteArrayInputStream(baos.toByteArray());
    }

    public static void addMyAuthorData(CurseGame game, Map<Integer, Set<Integer>> trace) {
        trace.get(6).add(500009);
        trace.get(6).add(492346);
        trace.get(6).add(353051);
        trace.get(6).add(420519);
        trace.get(6).add(386380);
        trace.get(6).add(400337);
        trace.get(6).add(356906);
        trace.get(6).add(345729);
        trace.get(6).add(331707);
        trace.get(6).add(315914);
        trace.get(6).add(362923);
        trace.get(6).add(354355);
        trace.get(6).add(298907);
        trace.get(6).add(334988);
        trace.get(6).add(342250);
        trace.get(6).add(521800);
        trace.get(6).add(503115);
    }

    public static JsonObject dateJo(ZonedDateTime time) {
        Instant instant = time.toInstant();
        Date stdate = Date.from(instant);
        JsonObject jo = new JsonObject();
        jo.addProperty("time", stdate.getTime());
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        jo.addProperty("format", time.format(f));
        return jo;
    }

}
