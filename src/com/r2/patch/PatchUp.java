package com.r2.patch;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;

public class PatchUp {

  static String tar = "patch.tar";
  static String VER = "1.0.0";

  /*    way/
   * using the bitbucket api (authorized by bitbucket app password)
   * get the list of changes from the given PR's then create the
   * tar file patch
   */
  public static void main(String[] args) throws Exception {
    if(args.length == 0) {
      showHelp();
      return;
    }
    String[] up = getUsernamePassword();
    if(up == null || up.length != 2) {
      showHelp();
      System.out.println("");
      System.out.println("**ERROR: Could not read '.patchup'");
      return;
    }
    String username = up[0];
    String password = up[1];

    String for_ = String.join(",", args);
    System.out.println("****************************************");
    System.out.println("PatchUp v" + VER);
    System.out.println("****************************************");
    System.out.println("Creates a patch (" + tar + ") to be deployed on production.");
    System.out.println("");
    System.out.println("Patching from PR: ");
    System.out.println("");
    System.out.println("    ** " + getWorkspace() + " (PR:" + for_ + ") **");

    System.out.println("");
    System.out.println(">>>>");
    List<String> changed = new ArrayList<>();
    for(String pr: args) {
      List<String> changed_ = getPRFiles(username, password, pr);
      for(String c : changed_) {
        if(!changed.contains(c)) changed.add(c);
      }
    }
    System.out.println("<<<<");
    createTarFile(changed);
  }

  /*    understand/
   * we expect the bitbucket username and app password as two
   * lines from in a file called '.patchup'
   */
  static String[] getUsernamePassword() {
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(".patchup")));
      String username = null;
      String password = null;
      String line;
      while((line = in.readLine()) != null) {
        line = line.trim();
        if(line.length() == 0) continue;
        if(username == null) username = line;
        else if(password == null) password = line;
      }
      in.close();
      if(username == null || password == null) return null;
      String[] up = new String[2];
      up[0] = username;
      up[1] = password;
      return up;
    } catch(Throwable t) {
      return null;
    }
  }

  /*    way/
   * given the PR number, get the PR info, extract the diff URL
   * and get the diff, then get the list of files from the
   * diff
   */
  static List<String> getPRFiles(String username, String password, String pr) throws Exception {
    System.out.println("Getting Pull Request " + pr + " meta data...");
    String info = getPRInfo(username, password, pr);
    System.out.println("Generating Diff...");
    String diffURL = getDiffURL(info);
    InputStream diff = getURL(username, password, diffURL);
    System.out.println("Finding changed files in diff...");
    return getChangedFiles(diff);
  }

  static String getPRInfo(String username, String password, String pr) throws Exception {
    String url = "https://api.bitbucket.org/2.0/repositories/salesboxtech/salesboxai-app/pullrequests/" + pr;
    InputStream in = getURL(username, password, url);
    byte[] buf = new byte[1024];
    int len;
    ByteArrayOutputStream res = new ByteArrayOutputStream();
    while((len = in.read(buf)) != -1) {
      res.write(buf, 0, len);
    }
    in.close();
    return res.toString("UTF-8");
  }

  static InputStream getURL(String username, String password, String url) throws Exception {
    URL u = new URL(url);
    URLConnection conn = u.openConnection();
    String up = username + ":" + password;
    byte[] enc = Base64.getEncoder().encode(up.getBytes());
    String auth = "Basic " + new String(enc);
    conn.setRequestProperty("Authorization", auth);
    return conn.getInputStream();
  }

  /*    way/
   * look for "diff " lines in the changes sent back
   */
  static List<String> getChangedFiles(InputStream is) throws Exception {
    BufferedReader in = new BufferedReader(new InputStreamReader(is));
    List<String> ret = new ArrayList<>();
    String line;
    while((line = in.readLine()) != null) {
      if(line.startsWith("diff ")) {
        int ndx = line.indexOf(" a/");
        line = line.substring(ndx + 3);
        ndx = line.indexOf(" ");
        if(ndx > 0) line = line.substring(0, ndx);
        ret.add(line);
      }
    }
    return ret;
  }

  /*    way/
   * we look for the "diff": key and the "https://..." url after
   * that
   */
  static String getDiffURL(String res) {
    int diff = res.indexOf("\"diff\":");
    res = res.substring(diff);
    int s = res.indexOf("\"https://");
    res = res.substring(s+1);
    int e = res.indexOf("\"");
    return res.substring(0, e);
  }

  /*    way/
   * get all the differences we have between the given commit
   * and the remote master and create a tar file of all
   * associated java classes and other files
   */
  public static void main1(String[] args) throws Exception {
    String branch = "remotes/origin/master";
    if(args.length > 0) branch = args[0];

    List<String> changes = getChanges(branch);
    List<String> changed = justNames(changes);
    createTarFile(changed);
  }

  static void createTarFile(List<String> changed) throws Exception {
    if(changed.size() == 0) {
      System.out.println("No changes found - nothing to do");
      return;
    }

    List<String> targets = findTargets(changed);
    List<String> missing = findMissing(changed);

    if(!shallWeGo(targets, missing, tar)) return;

    System.out.println("Creating Tar of the target files:");
    makeTar(targets, tar);
  }

  static void showHelp() {
    System.out.println("****************************************");
    System.out.println("PatchUp v" + VER);
    System.out.println("****************************************");
    System.out.println("Usage:");
    System.out.println("  1. Create a file called '.patchup' that contains two lines:");
    System.out.println("        bitbucket_username");
    System.out.println("        bitbucket_app_password");
    System.out.println("     You can create an app password in your bitbucket profile.");
    System.out.println("     .patchup (SAMPLE):");
    System.out.println("        sbox_charles");
    System.out.println("        MYSECRETPASSWORD");
    System.out.println("  2. Call:");
    System.out.println("        ./PatchUp.sh <Pull Request Num> <Pull Request Num>...");
  }

  static boolean shallWeGo(List<String> targets, List<String> missing, String tar) {
    System.out.println("");
    System.out.println("Patching files:");
    System.out.println("");
    for(String tgt : targets) {
      System.out.println("  " + tgt);
    }
    if(missing.size() > 0) {
      System.out.println("");
      System.out.println("WARNING - Ignoring:");
      System.out.println("");
      for(String m: missing) {
        System.out.println("  " + m);
      }
    }
    System.out.println("");
    System.out.println("CHECK THAT THIS ALL LOOK FINE AND THAT YOUR WORKSPACE");
    System.out.println("BUILD IS UP-TO-DATE BEFORE PROCEEDING.");
    System.out.println("");
    String ans = System.console().readLine("Proceed? [Y/*]:");
    return "Y".equalsIgnoreCase(ans);
  }

  /*    way/
   * make a tar file of the given files, showing the output
   * as we go
   */
  static void makeTar(List<String> files, String name) throws Exception {
    String cmd = "tar -cvf " + name;
    for(String f : files) {
      cmd += " " + f;
    }

    Process p = Runtime.getRuntime().exec(cmd);
    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
    BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));

    String line;
    while((line = reader.readLine()) != null) {
      System.out.println(line);
    }
    while((line = err.readLine()) != null) {
      System.out.println(line);
    }
    int exitVal = p.waitFor();
    System.out.println("");
    System.out.println("");
    System.out.println("");
    if(exitVal == 0) {
      System.out.println("Created: " + name);
    } else {
      System.out.println("PATCH CREATION FAILED!");
    }
  }

  /*    understand/
   * each returned line is of the form
   * M    file/name
   * A    file/name
   * (status) (filename)
   *
   *    way/
   * remove the status and just return the trimmed filename
   */
  static List<String> justNames(List<String> statuses) {
    return statuses.stream().map(s -> s.substring(1).trim()).collect(Collectors.toList());
  }

  /*    understand/
   * we need the associated class/html files for each file in the
   * targets directory (`targets/classes`). they could be generated
   * inner classes and implementations (which will have the same name
   * but continue with "xxxImpl.class" or "xxx$Inner.class")
   */
  static List<String> findTargets(List<String> changed) {
    List<String> ret = new ArrayList<>();
    for(String f: changed) {
      if(f.endsWith(".properties") || f.endsWith(".xml")) {
        continue;
      } else if(f.endsWith(".java")) {
        String t = f.replace("src", "target/classes");
        t = t.replace(".java", ".class");
        File tgt = new File(t);
        if(tgt.exists()) {
          ret.add(t);
          String pkg = tgt.getParent();
          if(pkg == null) pkg = ".";
          File[] all = new File(pkg).listFiles();
          String pfx = tgt.getName().replace(".class", "");
          for(File c : all) {
            String n = c.getName();
            if(!n.endsWith(".class")) continue;
            if(n.startsWith(pfx + "$")) ret.add(c.getPath());
            if(n.equals(pfx + "Impl.class")) ret.add(c.getPath());
          }
        }
      } else {
        String tgt = f.replace("src", "target/classes");
        if(new File(tgt).exists()) ret.add(tgt);
      }
    }
    return ret;
  }

  /*    way/
   * list those that are missing
   */
  static List<String> findMissing(List<String> changes) {
    List<String> ret = new ArrayList<>();
    for(String f: changes) {
      if(f.endsWith(".properties") || f.endsWith(".xml")) {
        ret.add(f);
      } else if(f.endsWith(".java")) {
        String t = f.replace("src", "target/classes");
        t = t.replace(".java", ".class");
        if(!new File(t).exists()) {
          ret.add(f);
        }
      } else {
        String t = f.replace("src", "target/classes");
        if(!new File(t).exists()) ret.add(f);
      }
    }
    return ret;
  }

  /*    way/
   * get the git diff between the current workspace and
   * the other branch
   */
  static List<String> getChanges(String against) throws Exception {
    String cmd = "git diff --name-status " + against;

    Process p = Runtime.getRuntime().exec(cmd);
    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

    List<String> ret = new ArrayList<>();
    String line;
    while((line = reader.readLine()) != null) {
      ret.add(line);
    }
    int exitVal = p.waitFor();

    return ret;
  }

  static String getWorkspace() throws Exception {
    String cmd = "git remote -v";

    Process p = Runtime.getRuntime().exec(cmd);
    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

    String workspace = "";
    String line;
    while((line = reader.readLine()) != null) {
      int n = line.lastIndexOf("salesboxai-app-");
      if(n != -1) {
        int m = line.lastIndexOf(".git");
        workspace = line.substring(n, m);
      }
    }
    int exitVal = p.waitFor();

    return workspace;
  }

  static String getBranch() throws Exception {
    String cmd = "git branch";

    Process p = Runtime.getRuntime().exec(cmd);
    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

    String branch = "";
    String line;
    while((line = reader.readLine()) != null) {
      if(line.startsWith("*")) branch = line.substring(1).trim();
    }
    int exitVal = p.waitFor();

    return branch;
  }
}
