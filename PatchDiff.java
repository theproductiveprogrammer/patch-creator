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

public class PatchDiff {

  static String tar = "bundle.tar";
  static String VER = "1.0.0";

  /*    way/
   * using the bitbucket api (authorized by bitbucket app password)
   * get the list of changes from the given PR's then create the
   * tar file patch
   */
  public static void main(String[] args) throws Exception {
    if(args.length != 1) {
      showHelp();
      return;
    }
    String from = args[0];

    String for_ = String.join(",", args);
    System.out.println("****************************************");
    System.out.println("PatchUp v" + VER);
    System.out.println("****************************************");
    System.out.println("Creates a tar (" + tar + ") to be deployed on production.");
    System.out.println("");
    System.out.println("Checking diff from " + from);
    System.out.println("");

    System.out.println("");
    System.out.println(">>>>");
    List<String> changes = getChanges(from);
    List<String> changed = new ArrayList<>();
    for(String c : changes) {
        if(changed.contains(c)) continue;
        changed.add(c);
        System.out.println("Found " + c);
    }
    System.out.println("<<<<");
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
    System.out.println("PatchDiff v" + VER);
    System.out.println("****************************************");
    System.out.println("Usage:");
    System.out.println("  1. Compile the script:");
    System.out.println("        $> javac PatchDiff.java");
    System.out.println("  2. Execute it providing the commit to start from:");
    System.out.println("        $> java PatchDiff <commit id>");
    System.out.println("     Eg.:");
    System.out.println("        $> java PatchDiff HEAD~3        <-- (3 commits back)");
    System.out.println("        $> java PatchDiff dbad1         <-- (specific commit)");
    System.out.println("        $> java PatchDiff rel-1.23      <-- (tag)");
  }

  static boolean shallWeGo(List<String> targets, List<String> missing, String tar) {
    System.out.println("");
    System.out.println("Zipping files:");
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
    System.out.println("CHECK THAT THIS ALL LOOKS FINE AND THAT YOUR WORKSPACE");
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
   * get the git diff in a parse-able format
   */
  static List<String> getChanges(String from) throws Exception {
    String cmd = "git diff --name-only " + from;

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

}
